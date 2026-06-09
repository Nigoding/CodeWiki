from __future__ import annotations

from dataclasses import asdict, is_dataclass
from datetime import datetime, timezone
from pathlib import Path
import json
import re
import shutil
from typing import Any

from javawiki_analyzer.models import JavaComponent


def write_artifacts(
    output_dir: Path,
    analysis: dict,
    module_tree: dict,
    order: dict,
    candidate_modules: dict,
    components: list[JavaComponent],
    dependencies_artifact: dict,
    component_to_module: dict[str, str],
) -> None:
    _prepare_output_dir(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "modules").mkdir(exist_ok=True)
    (output_dir / "components").mkdir(exist_ok=True)

    component_index = {"components": {}}
    component_artifact_paths: dict[str, str] = {}

    for component in components:
        artifact_path = f"components/{_safe_component_filename(component)}.json"
        component_artifact_paths[component.component_id] = artifact_path
        component_dict = _to_jsonable(component)
        component_dict["module_id"] = component_to_module.get(component.component_id)
        _write_json(output_dir / artifact_path, component_dict)

        component_index["components"][component.component_id] = {
            "component_id": component.component_id,
            "language": component.language,
            "kind": component.kind,
            "package": component.package,
            "qualified_name": component.qualified_name,
            "simple_name": component.simple_name,
            "file_path": component.file_path,
            "span": component.span,
            "annotations": component.annotations,
            "stereotype": component.stereotype,
            "module_id": component_to_module.get(component.component_id),
            "artifact_path": artifact_path,
            "remote_endpoint_count": len(component.remote_endpoints),
            "entry_point_count": len(component.entry_points),
        }

    for module_id, module in module_tree["modules"].items():
        module_components = [
            component for component in components if component_to_module.get(component.component_id) == module_id
        ]
        module_dependencies = _module_dependencies(module_id, dependencies_artifact, component_to_module)
        remote_endpoints_aggregated = [
            {**endpoint, "component_id": c.component_id, "qualified_name": c.qualified_name, "file_path": c.file_path}
            for c in module_components
            for endpoint in c.remote_endpoints
        ]
        external_systems = sorted({_external_system_of(endpoint) for endpoint in remote_endpoints_aggregated if _external_system_of(endpoint)})
        module_json = {
            **module,
            "spring_stereotypes": sorted({c.stereotype for c in module_components if c.stereotype}),
            "components": [
                {
                    "component_id": c.component_id,
                    "qualified_name": c.qualified_name,
                    "kind": c.kind,
                    "stereotype": c.stereotype,
                    "artifact_path": "../" + component_artifact_paths[c.component_id],
                }
                for c in module_components
            ],
            "parent_module_id": _find_parent(module_tree, module_id),
            "internal_dependencies": module_dependencies["internal"],
            "external_dependencies": module_dependencies["external"],
            "entry_points": [entry for c in module_components for entry in c.entry_points],
            "remote_endpoints": remote_endpoints_aggregated,
            "external_systems": external_systems,
            "important_files": sorted({c.file_path for c in module_components}),
            "diagnostics": [],
        }
        _write_json(output_dir / "modules" / f"{module_id}.json", module_json)

    _write_json(output_dir / "analysis.json", analysis)
    _write_json(output_dir / "candidate_modules.json", candidate_modules)
    _write_json(output_dir / "module_tree.json", module_tree)
    _write_json(output_dir / "processing_order.json", order)
    _write_json(output_dir / "component_index.json", component_index)
    _write_json(output_dir / "dependencies.json", dependencies_artifact)


def base_analysis(
    analysis_id: str,
    source: dict,
    build_system: dict,
    summary: dict,
    diagnostics: list[dict],
) -> dict:
    return {
        "schema_version": "1.1",
        "analysis_id": analysis_id,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": source,
        "build_system": build_system,
        "artifacts": {
            "module_tree": "module_tree.json",
            "processing_order": "processing_order.json",
            "candidate_modules": "candidate_modules.json",
            "component_index": "component_index.json",
            "dependencies": "dependencies.json",
            "modules_dir": "modules",
            "components_dir": "components",
        },
        "summary": summary,
        "diagnostics": diagnostics,
    }


def _prepare_output_dir(output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    for dirname in ("modules", "components"):
        target = output_dir / dirname
        if target.exists():
            shutil.rmtree(target)
    for filename in (
        "analysis.json",
        "module_tree.json",
        "processing_order.json",
        "candidate_modules.json",
        "component_index.json",
        "dependencies.json",
    ):
        target = output_dir / filename
        if target.exists():
            target.unlink()


def _write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def _to_jsonable(value: Any) -> Any:
    if is_dataclass(value):
        return asdict(value)
    if isinstance(value, list):
        return [_to_jsonable(v) for v in value]
    if isinstance(value, dict):
        return {k: _to_jsonable(v) for k, v in value.items()}
    return value


def _safe_component_filename(component: JavaComponent) -> str:
    base = component.qualified_name or component.component_id
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", base).strip("_")


def _find_parent(module_tree: dict, module_id: str) -> str | None:
    for candidate_id, module in module_tree["modules"].items():
        if module_id in module.get("child_module_ids", []):
            return candidate_id
    return None


def _external_system_of(endpoint: dict) -> str | None:
    url = endpoint.get("url") or ""
    match = re.match(r"https?://([^/]+)", url)
    if match:
        return match.group(1)
    placeholder = re.match(r"\$\{([^}/:]+)", url)
    if placeholder:
        return placeholder.group(1)
    return None


def _module_dependencies(module_id: str, dependencies_artifact: dict, component_to_module: dict[str, str]) -> dict:
    internal = []
    external = []
    for dep in dependencies_artifact["component_dependencies"]:
        from_module = component_to_module.get(dep["from_component_id"])
        to_module = component_to_module.get(dep["to_component_id"])
        if from_module == module_id and to_module == module_id:
            internal.append(dep)
        elif from_module == module_id and to_module != module_id:
            external.append({**dep, "to_module_id": to_module})
    return {"internal": internal, "external": external}
