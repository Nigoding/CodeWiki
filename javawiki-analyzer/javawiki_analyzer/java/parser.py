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
    "FeignClient": "remote_client_feign",
    "HttpExchange": "remote_client_http_exchange",
}

HTTP_MAPPING = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "DeleteMapping": "DELETE",
    "PatchMapping": "PATCH",
}

REMOTE_METHOD_ANNOTATIONS = {
    "GetExchange": "GET",
    "PostExchange": "POST",
    "PutExchange": "PUT",
    "DeleteExchange": "DELETE",
    "PatchExchange": "PATCH",
    "HttpExchange": "ANY",
    "RequestLine": "ANY",
}

REMOTE_CLIENT_TYPES = {
    "RestTemplate": "rest_template",
    "WebClient": "web_client",
    "OkHttpClient": "okhttp_client",
    "HttpClient": "http_client",
    "AsyncRestTemplate": "rest_template",
    "RestClient": "rest_client",
}

REMOTE_CLIENT_METHODS = {
    "rest_template": {
        "getForObject": "GET",
        "getForEntity": "GET",
        "postForObject": "POST",
        "postForEntity": "POST",
        "postForLocation": "POST",
        "put": "PUT",
        "delete": "DELETE",
        "patchForObject": "PATCH",
        "exchange": "ANY",
        "execute": "ANY",
    },
    "rest_client": {
        "get": "GET",
        "post": "POST",
        "put": "PUT",
        "delete": "DELETE",
        "patch": "PATCH",
        "method": "ANY",
    },
    "web_client": {
        "get": "GET",
        "post": "POST",
        "put": "PUT",
        "delete": "DELETE",
        "patch": "PATCH",
        "method": "ANY",
    },
    "okhttp_client": {
        "newCall": "ANY",
    },
    "http_client": {
        "send": "ANY",
        "sendAsync": "ANY",
    },
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
        component.remote_endpoints = _extract_remote_endpoints(component) + _remote_method_endpoints(component)
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
            simple_type = type_name.split("<", 1)[0].replace("[]", "").strip()
            fields.append(
                JavaField(
                    name=name,
                    type=type_name,
                    annotations=annotations,
                    injection=_field_injection(annotations),
                    remote_client_kind=REMOTE_CLIENT_TYPES.get(simple_type),
                )
            )
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
            call = {
                "target": text.split("(", 1)[0].strip(),
                "kind": "method_call",
                "line": child.start_point[0] + 1,
                "snippet": text if len(text) <= 400 else text[:400] + "...",
            }
            calls.append(call)
        elif child.type == "object_creation_expression":
            text = _node_text(child, source) or ""
            match = re.search(r"new\s+([A-Za-z_][A-Za-z0-9_$.]*)", text)
            if match:
                calls.append(
                    {
                        "target": match.group(1),
                        "kind": "object_creation",
                        "line": child.start_point[0] + 1,
                    }
                )
    return calls


def _extract_remote_endpoints(component: JavaComponent) -> list[dict[str, Any]]:
    """
    Detect remote HTTP calls made through RestTemplate/WebClient/HttpClient/etc.

    Walks every method's `calls` list and recognises invocations targeting a
    declared remote client field (e.g. `restTemplate.getForObject(...)`).
    Extracts the first string literal or `${...}` placeholder argument as the
    URL candidate. Appends entries to each method's `remote_endpoints` and
    returns the aggregated list for the component.
    """
    remote_fields = {f.name: f.remote_client_kind for f in component.fields if f.remote_client_kind}
    aggregated: list[dict[str, Any]] = []
    for method in component.methods:
        for call in method.calls:
            target = str(call.get("target") or "")
            if "." not in target:
                continue
            head, _, tail = target.partition(".")
            client_kind = remote_fields.get(head)
            if not client_kind:
                continue
            method_chain = tail.split(".")
            method_table = REMOTE_CLIENT_METHODS.get(client_kind, {})
            http_method: str | None = None
            target_method: str | None = None
            for piece in method_chain:
                if piece in method_table:
                    http_method = method_table[piece]
                    target_method = piece
                    break
            if not target_method:
                continue
            snippet = str(call.get("snippet") or "")
            url, literal_source = _first_url_literal(snippet)
            endpoint = {
                "kind": client_kind,
                "http_method": http_method,
                "target_method": target_method,
                "client_field": head,
                "url": url,
                "literal_source": literal_source,
                "line": call.get("line"),
            }
            method.remote_endpoints.append(endpoint)
            aggregated.append({**endpoint, "via_method": method.name})
    return aggregated


def _first_url_literal(snippet: str) -> tuple[str | None, str | None]:
    """Return (url, literal_source) where literal_source is 'string' or 'placeholder' or None."""
    if not snippet:
        return None, None
    args_part = snippet.split("(", 1)[1] if "(" in snippet else snippet
    string_match = re.search(r'"([^"]+)"', args_part)
    if string_match:
        return string_match.group(1), "string"
    placeholder_match = re.search(r"\$\{([^}]+)\}", args_part)
    if placeholder_match:
        return "${" + placeholder_match.group(1) + "}", "placeholder"
    return None, None


def _remote_method_endpoints(component: JavaComponent) -> list[dict[str, Any]]:
    """
    For @FeignClient / @HttpExchange interfaces, every method with
    @GetExchange / @PostExchange / @RequestMapping etc. is an outbound endpoint.
    """
    if component.stereotype not in {"remote_client_feign", "remote_client_http_exchange"}:
        return []
    class_base = _mapping_path(component.source_code, ["RequestMapping", "HttpExchange"])
    endpoints: list[dict[str, Any]] = []
    sorted_methods = sorted(
        [m for m in component.methods if m.span],
        key=lambda m: m.span["start_line"],
    )
    span_index = {id(m): i for i, m in enumerate(sorted_methods)}
    for method in component.methods:
        http_method: str | None = None
        used_annotation: str | None = None
        for annotation in method.annotations:
            if annotation in REMOTE_METHOD_ANNOTATIONS:
                http_method = REMOTE_METHOD_ANNOTATIONS[annotation]
                used_annotation = annotation
                break
            if annotation in HTTP_MAPPING:
                http_method = HTTP_MAPPING[annotation]
                used_annotation = annotation
                break
            if annotation == "RequestMapping":
                http_method = "ANY"
                used_annotation = annotation
                break
        if not used_annotation:
            continue
        method_path = _mapping_path_for_method(
            component, method, sorted_methods, span_index, [used_annotation]
        )
        endpoint = {
            "kind": component.stereotype,
            "http_method": http_method,
            "target_method": method.name,
            "client_field": None,
            "url": _join_paths(class_base, method_path),
            "literal_source": "annotation",
            "line": method.span.get("start_line") if method.span else None,
        }
        method.remote_endpoints.append(endpoint)
        endpoints.append({**endpoint, "via_method": method.name})
    return endpoints


def _entry_points(component: JavaComponent, source: str) -> list[dict[str, Any]]:
    if component.stereotype not in {"rest_controller", "controller"}:
        return []

    class_base = _mapping_path(component.source_code, ["RequestMapping"])
    entry_points = []
    sorted_methods = sorted(
        [m for m in component.methods if m.span],
        key=lambda m: m.span["start_line"],
    )
    span_index = {id(m): i for i, m in enumerate(sorted_methods)}
    for method in component.methods:
        mapping = _method_mapping(
            method.annotations,
            component,
            method,
            sorted_methods,
            span_index,
        )
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


def _method_mapping(
    annotations: list[str],
    component: JavaComponent,
    method: JavaMethod,
    sorted_methods: list[JavaMethod],
    span_index: dict[int, int],
) -> tuple[str, str] | None:
    for annotation in annotations:
        if annotation in HTTP_MAPPING:
            return HTTP_MAPPING[annotation], _mapping_path_for_method(
                component, method, sorted_methods, span_index, [annotation]
            )
        if annotation == "RequestMapping":
            return "ANY", _mapping_path_for_method(
                component, method, sorted_methods, span_index, ["RequestMapping"]
            )
    return None


def _mapping_path(source: str, annotation_names: list[str]) -> str:
    for name in annotation_names:
        match = re.search(rf"@{name}\s*(?:\(([^)]*)\))?", source)
        if match:
            return _extract_path_arg(match.group(1) or "")
    return ""


def _mapping_path_for_method(
    component: JavaComponent,
    method: JavaMethod,
    sorted_methods: list[JavaMethod],
    span_index: dict[int, int],
    annotation_names: list[str],
) -> str:
    if not method.span or not component.span:
        return ""
    class_lines = component.source_code.splitlines()
    class_start_line = component.span["start_line"]
    method_start_rel = method.span["start_line"] - class_start_line
    method_end_rel = method.span["end_line"] - class_start_line
    if method_start_rel < 0 or method_start_rel >= len(class_lines):
        return ""
    method_end_rel = min(method_end_rel, len(class_lines) - 1)
    window = "\n".join(class_lines[method_start_rel : method_end_rel + 1])
    return _mapping_path(window, annotation_names)


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
