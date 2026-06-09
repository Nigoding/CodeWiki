from __future__ import annotations

from collections import defaultdict
import re

from javawiki_analyzer.models import Dependency, JavaComponent, MavenModule


LAYER_BY_STEREOTYPE = {
    "rest_controller": "api",
    "controller": "api",
    "service": "application",
    "component": "application",
    "repository": "persistence",
    "configuration": "config",
}

LAYER_KEYWORDS = {
    "controller": "api",
    "api": "api",
    "dto": "api",
    "request": "api",
    "response": "api",
    "service": "application",
    "application": "application",
    "repository": "persistence",
    "dao": "persistence",
    "mapper": "persistence",
    "infrastructure": "persistence",
    "entity": "domain",
    "domain": "domain",
    "model": "domain",
    "config": "config",
    "client": "integration",
    "interfaces": "api",
    "interface": "api",
    "web": "api",
}


def build_candidate_modules(
    components: list[JavaComponent],
    dependencies: list[Dependency],
    maven_modules: list[MavenModule],
) -> dict:
    """Build deterministic grouping evidence for an agent-side aggregation phase."""
    candidates: dict[str, dict] = {}
    component_candidates: dict[str, list[str]] = defaultdict(list)

    def add_candidate(
        source: str,
        key: str,
        name: str,
        rationale: str,
        comps: list[JavaComponent],
        confidence: str,
        maven_module: MavenModule | None = None,
    ) -> None:
        if not comps:
            return
        candidate_id = f"{source}:{_slug(key)}"
        packages = sorted({component.package for component in comps if component.package})
        maven_ids = sorted({component.maven_module for component in comps if component.maven_module})
        stereotypes = sorted({component.stereotype for component in comps if component.stereotype})
        candidates[candidate_id] = {
            "candidate_id": candidate_id,
            "name": name,
            "source": source,
            "confidence": confidence,
            "rationale": rationale,
            "maven_module": _maven_module_dict(maven_module) if maven_module else None,
            "maven_module_ids": maven_ids,
            "packages": packages,
            "spring_stereotypes": stereotypes,
            "component_ids": sorted(component.component_id for component in comps),
            "entry_points_count": sum(len(component.entry_points) for component in comps),
        }
        for component in comps:
            component_candidates[component.component_id].append(candidate_id)

    by_maven: dict[str, list[JavaComponent]] = defaultdict(list)
    by_business: dict[str, list[JavaComponent]] = defaultdict(list)
    by_layer: dict[str, list[JavaComponent]] = defaultdict(list)
    by_entrypoint_context: dict[str, list[JavaComponent]] = defaultdict(list)

    maven_by_id = {module.module_id: module for module in maven_modules}

    for component in components:
        if component.maven_module:
            by_maven[component.maven_module].append(component)
        business_context = _business_context(component)
        by_business[business_context].append(component)
        by_layer[_layer(component)].append(component)
        if component.entry_points:
            by_entrypoint_context[business_context].append(component)

    for module_id, comps in sorted(by_maven.items()):
        module = maven_by_id.get(module_id)
        name = module.artifact_id if module else module_id
        add_candidate(
            source="maven",
            key=module_id,
            name=_title(name),
            rationale="Physical Maven module boundary discovered from pom.xml.",
            comps=comps,
            confidence="high",
            maven_module=module,
        )

    for context, comps in sorted(by_business.items()):
        add_candidate(
            source="package_context",
            key=context,
            name=_title(context),
            rationale="Package namespace suggests a business or technical context.",
            comps=comps,
            confidence="medium",
        )

    for layer, comps in sorted(by_layer.items()):
        add_candidate(
            source="technical_layer",
            key=layer,
            name=_title(layer),
            rationale="Spring stereotype or package segment suggests a technical layer.",
            comps=comps,
            confidence="medium",
        )

    for context, comps in sorted(by_entrypoint_context.items()):
        add_candidate(
            source="entrypoint_group",
            key=f"{context}-entrypoints",
            name=f"{_title(context)} Entry Points",
            rationale="REST controllers expose public entry points for this context.",
            comps=comps,
            confidence="medium",
        )

    return {
        "schema_version": "1.0",
        "purpose": "Deterministic candidate boundaries for agent-side LLM module aggregation.",
        "candidates": candidates,
        "component_candidates": {
            component_id: sorted(candidate_ids)
            for component_id, candidate_ids in sorted(component_candidates.items())
        },
        "dependency_summary": _candidate_dependency_summary(candidates, component_candidates, dependencies),
        "notes": [
            "candidate_modules.json is not the final documentation module tree.",
            "An agent may use these candidates with component_index.json and dependencies.json to write final module_tree.json.",
        ],
    }


def classify_modules(components: list[JavaComponent], dependencies: list[Dependency]) -> tuple[dict, dict[str, str]]:
    module_components: dict[str, list[JavaComponent]] = defaultdict(list)
    parent_children: dict[str, set[str]] = defaultdict(set)

    for component in components:
        context = _context(component)
        layer = _layer(component)
        parent_id = _slug(context)
        leaf_id = _slug(f"{context}-{layer}")
        module_components[leaf_id].append(component)
        parent_children[parent_id].add(leaf_id)

    component_to_module = {}
    modules = {}
    root_modules = sorted(parent_children.keys())

    for parent_id in root_modules:
        children = sorted(parent_children[parent_id])
        modules[parent_id] = {
            "module_id": parent_id,
            "name": _title(parent_id),
            "kind": "parent",
            "doc_path": f"modules/{parent_id}.md",
            "maven_module": None,
            "packages": sorted(_packages_for_children(children, module_components)),
            "component_ids": [],
            "child_module_ids": children,
            "depends_on_module_ids": [],
        }

    for module_id, comps in module_components.items():
        for component in comps:
            component_to_module[component.component_id] = module_id
        packages = sorted({component.package for component in comps if component.package})
        maven_modules = sorted({component.maven_module for component in comps if component.maven_module})
        modules[module_id] = {
            "module_id": module_id,
            "name": _title(module_id),
            "kind": "leaf",
            "doc_path": f"modules/{module_id}.md",
            "maven_module": maven_modules[0] if len(maven_modules) == 1 else None,
            "packages": packages,
            "component_ids": sorted(component.component_id for component in comps),
            "child_module_ids": [],
            "depends_on_module_ids": [],
        }

    for dep in dependencies:
        from_module = component_to_module.get(dep.from_component_id)
        to_module = component_to_module.get(dep.to_component_id)
        if from_module and to_module and from_module != to_module:
            deps = modules[from_module]["depends_on_module_ids"]
            if to_module not in deps:
                deps.append(to_module)

    for module in modules.values():
        module["depends_on_module_ids"] = sorted(module["depends_on_module_ids"])

    return {"root_modules": root_modules, "modules": modules}, component_to_module


def processing_order(module_tree: dict) -> dict:
    modules = module_tree["modules"]
    steps = []

    def visit(module_id: str) -> None:
        module = modules[module_id]
        for child_id in module.get("child_module_ids", []):
            visit(child_id)
        steps.append({"module_id": module_id, "kind": module["kind"]})

    for root_id in module_tree["root_modules"]:
        visit(root_id)

    return {"steps": steps, "overview_after_modules": True}


def _context(component: JavaComponent) -> str:
    parts = component.package.split(".") if component.package else []
    if len(parts) >= 3 and parts[0] in {"com", "org", "net", "io"}:
        return parts[2]
    if len(parts) >= 1:
        return parts[-2] if len(parts) > 1 and _layer_from_part(parts[-1]) else parts[-1]
    return component.maven_module or "root"


def _business_context(component: JavaComponent) -> str:
    parts = component.package.split(".") if component.package else []
    if "biz" in parts:
        start = parts.index("biz") + 1
        context_parts = []
        for part in parts[start:]:
            if _layer_from_part(part):
                break
            context_parts.append(part)
        if context_parts:
            return "-".join(context_parts)

    if len(parts) > 1 and _layer_from_part(parts[-1]):
        return parts[-2]

    for marker in ("application", "domain", "infrastructure", "interfaces", "interface"):
        if marker in parts:
            index = parts.index(marker)
            if index > 0:
                return parts[index - 1]

    if component.maven_module:
        normalized = re.sub(r"-(application|domain|infrastructure|interface|interfaces|api|web)$", "", component.maven_module)
        return normalized

    return _context(component)


def _layer(component: JavaComponent) -> str:
    if component.stereotype in LAYER_BY_STEREOTYPE:
        return LAYER_BY_STEREOTYPE[component.stereotype]
    parts = component.package.split(".")
    for part in reversed(parts):
        layer = _layer_from_part(part)
        if layer:
            return layer
    return "core"


def _layer_from_part(part: str) -> str | None:
    return LAYER_KEYWORDS.get(part.lower())


def _slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-") or "module"


def _title(value: str) -> str:
    return " ".join(part.capitalize() for part in value.split("-"))


def _packages_for_children(children: list[str], module_components: dict[str, list[JavaComponent]]) -> set[str]:
    packages = set()
    for child in children:
        packages.update(component.package for component in module_components.get(child, []) if component.package)
    return packages


def _maven_module_dict(module: MavenModule | None) -> dict | None:
    if not module:
        return None
    return {
        "module_id": module.module_id,
        "artifact_id": module.artifact_id,
        "path": module.path,
        "pom_path": module.pom_path,
        "group_id": module.group_id,
        "version": module.version,
    }


def _candidate_dependency_summary(
    candidates: dict[str, dict],
    component_candidates: dict[str, list[str]],
    dependencies: list[Dependency],
) -> list[dict]:
    summary: dict[tuple[str, str, str], int] = defaultdict(int)
    for dep in dependencies:
        from_candidates = component_candidates.get(dep.from_component_id, [])
        to_candidates = component_candidates.get(dep.to_component_id, [])
        for from_candidate in from_candidates:
            for to_candidate in to_candidates:
                if from_candidate != to_candidate:
                    if candidates[from_candidate]["source"] != candidates[to_candidate]["source"]:
                        continue
                    summary[(from_candidate, to_candidate, dep.kind)] += 1

    return [
        {
            "from_candidate_id": from_id,
            "to_candidate_id": to_id,
            "kind": kind,
            "count": count,
            "from_source": candidates[from_id]["source"],
            "to_source": candidates[to_id]["source"],
        }
        for (from_id, to_id, kind), count in sorted(summary.items())
    ]
