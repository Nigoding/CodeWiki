from __future__ import annotations

import re
from collections import defaultdict

from javawiki_analyzer.models import Dependency, JavaComponent


JAVA_BUILTINS = {
    "String",
    "Object",
    "List",
    "Map",
    "Set",
    "Collection",
    "Optional",
    "Integer",
    "Long",
    "Boolean",
    "Double",
    "Float",
    "BigDecimal",
    "LocalDate",
    "LocalDateTime",
    "void",
}


def resolve_component_links(components: list[JavaComponent]) -> list[Dependency]:
    by_qn = {component.qualified_name: component for component in components}
    by_simple = defaultdict(list)
    for component in components:
        by_simple[component.simple_name].append(component)

    dependencies: list[Dependency] = []

    for component in components:
        for field in component.fields:
            target = _resolve_type(field.type, component, by_qn, by_simple)
            if target:
                field.resolved_type = target.qualified_name
                if field.injection == "constructor":
                    kind = "constructor_injection"
                elif field.injection == "field":
                    kind = "field_injection"
                else:
                    kind = "field_type"
                dependencies.append(Dependency(component.component_id, target.component_id, kind, field.name))

        if component.extends:
            target = _resolve_type(component.extends, component, by_qn, by_simple)
            if target:
                dependencies.append(Dependency(component.component_id, target.component_id, "extends"))

        for iface in component.implements:
            target = _resolve_type(iface, component, by_qn, by_simple)
            if target:
                dependencies.append(Dependency(component.component_id, target.component_id, "implements"))

        for method in component.methods:
            for call in method.calls:
                target_name = str(call.get("target", "")).split(".", 1)[0]
                target = _resolve_type(target_name, component, by_qn, by_simple)
                if target:
                    call["resolved_component"] = target.component_id
                    dependencies.append(Dependency(component.component_id, target.component_id, call.get("kind", "method_call"), method.name))

        for endpoint in component.remote_endpoints:
            detail = f"{endpoint.get('http_method') or 'ANY'} {endpoint.get('url') or '<unresolved>'} via {endpoint.get('via_method') or endpoint.get('target_method')}"
            external_target = _external_target_id(endpoint)
            dependencies.append(
                Dependency(component.component_id, external_target, "remote_call", detail)
            )

    return _dedupe(dependencies)


def _external_target_id(endpoint: dict) -> str:
    """Build a stable virtual component id for the external system this call targets."""
    url = endpoint.get("url") or ""
    host = _extract_host(url)
    if host:
        return f"external::{host}"
    kind = endpoint.get("kind") or "remote"
    return f"external::{kind}:unresolved"


def _extract_host(url: str) -> str | None:
    if not url:
        return None
    match = re.match(r"https?://([^/]+)", url)
    if match:
        return match.group(1)
    placeholder = re.match(r"\$\{([^}/:]+)", url)
    if placeholder:
        return placeholder.group(1)
    return None


def build_dependency_artifact(dependencies: list[Dependency], component_to_module: dict[str, str]) -> dict:
    module_edges: dict[tuple[str, str], dict] = {}
    component_edges = []
    external_edges: dict[tuple[str, str], dict] = {}
    for dep in dependencies:
        component_edges.append(dep.__dict__)
        from_module = component_to_module.get(dep.from_component_id)
        to_id = dep.to_component_id
        if to_id.startswith("external::") and from_module:
            key = (from_module, to_id)
            edge = external_edges.setdefault(
                key,
                {
                    "from_module_id": from_module,
                    "external_target": to_id,
                    "count": 0,
                    "examples": [],
                },
            )
            edge["count"] += 1
            if len(edge["examples"]) < 5:
                edge["examples"].append(dep.__dict__)
            continue
        to_module = component_to_module.get(to_id)
        if not from_module or not to_module or from_module == to_module:
            continue
        key = (from_module, to_module)
        edge = module_edges.setdefault(
            key,
            {
                "from_module_id": from_module,
                "to_module_id": to_module,
                "count": 0,
                "kinds": [],
                "examples": [],
            },
        )
        edge["count"] += 1
        if dep.kind not in edge["kinds"]:
            edge["kinds"].append(dep.kind)
        if len(edge["examples"]) < 5:
            edge["examples"].append(dep.__dict__)

    return {
        "component_dependencies": component_edges,
        "module_dependencies": list(module_edges.values()),
        "external_module_dependencies": list(external_edges.values()),
    }


def _resolve_type(type_name: str, owner: JavaComponent, by_qn: dict, by_simple: dict) -> JavaComponent | None:
    if not type_name or type_name in JAVA_BUILTINS:
        return None
    clean = type_name.split("<", 1)[0].replace("[]", "").strip()
    if clean in by_qn:
        return by_qn[clean]

    for imp in owner.imports:
        if imp.endswith("." + clean) and imp in by_qn:
            return by_qn[imp]

    same_package = f"{owner.package}.{clean}" if owner.package else clean
    if same_package in by_qn:
        return by_qn[same_package]

    candidates = by_simple.get(clean, [])
    if len(candidates) == 1:
        return candidates[0]
    return None


def _dedupe(dependencies: list[Dependency]) -> list[Dependency]:
    seen = set()
    result = []
    for dep in dependencies:
        key = (dep.from_component_id, dep.to_component_id, dep.kind, dep.detail)
        if key not in seen:
            seen.add(key)
            result.append(dep)
    return result
