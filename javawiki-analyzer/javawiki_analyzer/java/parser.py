from __future__ import annotations

from pathlib import Path
import re
from typing import Any

from tree_sitter import Language, Parser
import tree_sitter_java

from javawiki_analyzer.models import JavaComponent, JavaField, JavaMethod, MavenModule


SPRING_STEREOTYPES = {
    "RestController": "rest_controller",
    "Controller": "controller",
    "Service": "service",
    "Repository": "repository",
    "Component": "component",
    "Configuration": "configuration",
}

HTTP_MAPPING = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "DeleteMapping": "DELETE",
    "PatchMapping": "PATCH",
}


def parse_java_file(repo_path: Path, file_path: Path, module: MavenModule) -> list[JavaComponent]:
    source = file_path.read_text(encoding="utf-8", errors="replace")
    parser = _new_parser()
    tree = parser.parse(source.encode("utf-8"))
    root = tree.root_node
    package = _extract_package(root) or ""
    imports = _extract_imports(root)
    rel_path = file_path.relative_to(repo_path).as_posix()
    lines = source.splitlines()

    components: list[JavaComponent] = []
    for node in _walk(root):
        if node.type not in {
            "class_declaration",
            "interface_declaration",
            "enum_declaration",
            "record_declaration",
            "annotation_type_declaration",
        }:
            continue

        name = _declaration_name(node, source)
        if not name:
            continue
        kind = _kind(node.type)
        qualified = f"{package}.{name}" if package else name
        annotations = _annotations_before(node, source)
        modifiers = _modifiers(node, source)
        body = _child_by_type(node, "class_body") or _child_by_type(node, "interface_body")
        class_source = _node_text(node, source)
        component_id = f"{rel_path}::{qualified}"
        component = JavaComponent(
            component_id=component_id,
            language="java",
            kind=kind,
            package=package,
            qualified_name=qualified,
            simple_name=name,
            file_path=rel_path,
            span={"start_line": node.start_point[0] + 1, "end_line": node.end_point[0] + 1},
            annotations=annotations,
            modifiers=modifiers,
            stereotype=_stereotype(annotations),
            extends=_extends(node, source),
            implements=_implements(node, source),
            imports=imports,
            source_code="\n".join(lines[node.start_point[0] : node.end_point[0] + 1]),
            maven_module=module.module_id,
        )
        if body is not None:
            component.fields = _fields(body, source)
            component.methods = _methods(body, name, source)
            _mark_constructor_injection(component)
        component.entry_points = _entry_points(component, source)
        components.append(component)

    return components


def _new_parser() -> Parser:
    java_language = Language(tree_sitter_java.language())
    try:
        return Parser(java_language)
    except TypeError:
        parser = Parser()
        parser.set_language(java_language)
        return parser


def _walk(node):
    yield node
    for child in node.children:
        yield from _walk(child)


def _node_text(node, source: str) -> str | None:
    if node is None:
        return None
    source_bytes = source.encode("utf-8")
    return source_bytes[node.start_byte : node.end_byte].decode("utf-8", errors="replace")


def _child_by_field(node, field: str):
    try:
        return node.child_by_field_name(field)
    except Exception:
        return None


def _declaration_name(node, source: str) -> str | None:
    for child in node.children:
        if child.type == "identifier":
            return _node_text(child, source)
    text = _node_text(_child_by_field(node, "name"), source)
    if text and "\n" not in text:
        return text
    return None


def _method_return_type(node, source: str) -> str | None:
    candidates = []
    for child in node.children:
        if child.type == "identifier":
            break
        if child.type in {
            "type_identifier",
            "generic_type",
            "scoped_type_identifier",
            "integral_type",
            "floating_point_type",
            "boolean_type",
            "void_type",
            "array_type",
        }:
            candidates.append(_node_text(child, source))
    if candidates:
        return candidates[-1]
    text = _node_text(_child_by_field(node, "type"), source)
    if text and "\n" not in text:
        return text
    return None


def _child_by_type(node, node_type: str):
    return next((child for child in node.children if child.type == node_type), None)


def _extract_package(root, source: str | None = None) -> str | None:
    for child in root.children:
        if child.type == "package_declaration":
            text = child.text.decode("utf-8", errors="replace")
            return text.replace("package", "").replace(";", "").strip()
    return None


def _extract_imports(root) -> list[str]:
    imports = []
    for child in root.children:
        if child.type == "import_declaration":
            text = child.text.decode("utf-8", errors="replace")
            text = text.replace("import", "").replace("static", "").replace(";", "").strip()
            imports.append(text)
    return imports


def _kind(node_type: str) -> str:
    return {
        "class_declaration": "class",
        "interface_declaration": "interface",
        "enum_declaration": "enum",
        "record_declaration": "record",
        "annotation_type_declaration": "annotation",
    }[node_type]


def _annotations_before(node, source: str) -> list[str]:
    annotations = []
    for child in node.children:
        if child.type == "modifiers":
            annotations.extend(_annotations_in(child, source))
    return annotations


def _annotations_in(node, source: str) -> list[str]:
    names = []
    for child in _walk(node):
        if child.type in {"marker_annotation", "annotation"}:
            text = _node_text(child, source) or ""
            match = re.match(r"@([A-Za-z_][A-Za-z0-9_$.]*)", text.strip())
            if match:
                names.append(match.group(1).split(".")[-1])
    return names


def _modifiers(node, source: str) -> list[str]:
    values = []
    for child in node.children:
        if child.type == "modifiers":
            for item in child.children:
                text = _node_text(item, source) or ""
                if text in {"public", "private", "protected", "static", "final", "abstract"}:
                    values.append(text)
    return values


def _stereotype(annotations: list[str]) -> str | None:
    for annotation in annotations:
        if annotation in SPRING_STEREOTYPES:
            return SPRING_STEREOTYPES[annotation]
    return None


def _extends(node, source: str) -> str | None:
    superclass = _child_by_type(node, "superclass")
    if superclass is None:
        return None
    return _last_identifier(_node_text(superclass, source) or "")


def _implements(node, source: str) -> list[str]:
    impl = _child_by_type(node, "super_interfaces")
    if impl is None:
        return []
    text = _node_text(impl, source) or ""
    text = text.replace("implements", "").strip()
    return [_last_identifier(part) for part in text.split(",") if _last_identifier(part)]


def _fields(body, source: str) -> list[JavaField]:
    fields = []
    for node in body.children:
        if node.type != "field_declaration":
            continue
        annotations = _annotations_in(node, source)
        text = _node_text(node, source) or ""
        cleaned = re.sub(r"@\w+(?:\([^)]*\))?", "", text).strip().rstrip(";")
        match = re.search(r"([A-Za-z_][A-Za-z0-9_<>, ?.\[\]]+)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:=|$)", cleaned)
        if match:
            type_name = _compact_type(match.group(1).split()[-1])
            name = match.group(2)
            fields.append(JavaField(name=name, type=type_name, annotations=annotations, injection=_field_injection(annotations)))
    return fields


def _methods(body, class_name: str, source: str) -> list[JavaMethod]:
    methods = []
    for node in body.children:
        if node.type not in {"method_declaration", "constructor_declaration"}:
            continue
        annotations = _annotations_in(node, source)
        name = _declaration_name(node, source) or class_name
        return_type = None
        if node.type == "method_declaration":
            return_type = _method_return_type(node, source)
        params = _parameters(node, source)
        calls = _calls(node, source)
        methods.append(
            JavaMethod(
                name=name,
                signature=f"{name}({', '.join(params)})",
                return_type=return_type,
                annotations=annotations,
                calls=calls,
                span={"start_line": node.start_point[0] + 1, "end_line": node.end_point[0] + 1},
            )
        )
    return methods


def _parameters(node, source: str) -> list[str]:
    params_node = _child_by_field(node, "parameters")
    if params_node is None:
        return []
    params = []
    for child in params_node.children:
        if child.type in {"formal_parameter", "spread_parameter"}:
            params.append(" ".join((_node_text(child, source) or "").split()))
    return params


def _calls(node, source: str) -> list[dict[str, Any]]:
    calls = []
    for child in _walk(node):
        if child.type == "method_invocation":
            text = _node_text(child, source) or ""
            calls.append({"target": text.split("(", 1)[0].strip(), "kind": "method_call"})
        elif child.type == "object_creation_expression":
            text = _node_text(child, source) or ""
            match = re.search(r"new\s+([A-Za-z_][A-Za-z0-9_$.]*)", text)
            if match:
                calls.append({"target": match.group(1), "kind": "object_creation"})
    return calls


def _entry_points(component: JavaComponent, source: str) -> list[dict[str, Any]]:
    if component.stereotype not in {"rest_controller", "controller"}:
        return []

    class_base = _mapping_path(component.source_code, ["RequestMapping"])
    entry_points = []
    for method in component.methods:
        mapping = _method_mapping(method.annotations, component.source_code, method.name)
        if mapping:
            http_method, method_path = mapping
            entry_points.append(
                {
                    "type": "rest_endpoint",
                    "http_method": http_method,
                    "path": _join_paths(class_base, method_path),
                    "class": component.qualified_name,
                    "method": method.name,
                    "response_type": method.return_type,
                }
            )
    return entry_points


def _mark_constructor_injection(component: JavaComponent) -> None:
    constructors = [method for method in component.methods if method.name == component.simple_name]
    if not constructors:
        return
    joined_params = "\n".join(param for ctor in constructors for param in _signature_params(ctor.signature))
    for field in component.fields:
        if field.name in joined_params or field.type in joined_params:
            field.injection = "constructor"


def _signature_params(signature: str) -> list[str]:
    if "(" not in signature or ")" not in signature:
        return []
    inner = signature.split("(", 1)[1].rsplit(")", 1)[0]
    return [part.strip() for part in inner.split(",") if part.strip()]


def _method_mapping(annotations: list[str], class_source: str, method_name: str) -> tuple[str, str] | None:
    for annotation in annotations:
        if annotation in HTTP_MAPPING:
            return HTTP_MAPPING[annotation], _mapping_path_for_method(class_source, method_name, [annotation])
        if annotation == "RequestMapping":
            return "ANY", _mapping_path_for_method(class_source, method_name, [annotation])
    return None


def _mapping_path(source: str, annotation_names: list[str]) -> str:
    for name in annotation_names:
        match = re.search(rf"@{name}\s*(?:\(([^)]*)\))?", source)
        if match:
            return _extract_path_arg(match.group(1) or "")
    return ""


def _mapping_path_for_method(source: str, method_name: str, annotation_names: list[str]) -> str:
    idx = source.find(method_name)
    prefix = source[max(0, idx - 500) : idx] if idx >= 0 else source
    return _mapping_path(prefix, annotation_names)


def _extract_path_arg(args: str) -> str:
    match = re.search(r'"([^"]+)"', args)
    if match:
        return match.group(1)
    return ""


def _join_paths(base: str, path: str) -> str:
    joined = "/".join(part.strip("/") for part in [base, path] if part)
    return "/" + joined if joined else "/"


def _field_injection(annotations: list[str]) -> str | None:
    if "Autowired" in annotations or "Resource" in annotations or "Inject" in annotations:
        return "field"
    return None


def _compact_type(value: str) -> str:
    return value.strip().replace("...", "[]")


def _last_identifier(value: str) -> str | None:
    match = re.findall(r"[A-Za-z_][A-Za-z0-9_$.]*", value)
    if not match:
        return None
    return match[-1].split(".")[-1]
