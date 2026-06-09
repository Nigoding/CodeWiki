from __future__ import annotations

from pathlib import Path
import re
import xml.etree.ElementTree as ET

from javawiki_analyzer.models import MavenModule


def scan_maven_modules(repo_path: Path) -> tuple[dict, list[MavenModule], list[dict]]:
    diagnostics: list[dict] = []
    root_pom = repo_path / "pom.xml"
    if not root_pom.exists():
        diagnostics.append({"level": "warning", "message": "No root pom.xml found; treating repository as a single Java source root."})
        module = MavenModule("root", repo_path.name, ".", "pom.xml")
        return {"type": "maven", "root_pom": None, "modules": [module.__dict__]}, [module], diagnostics

    root_info = _parse_pom(root_pom)
    modules: list[MavenModule] = []
    seen_paths: set[str] = set()

    if root_info.get("modules"):
        artifact_id = root_info.get("artifact_id") or repo_path.name
        if (repo_path / "src" / "main" / "java").exists():
            modules.append(
                MavenModule(
                    module_id=_slug(artifact_id),
                    artifact_id=artifact_id,
                    path=".",
                    pom_path="pom.xml",
                    group_id=root_info.get("group_id"),
                    version=root_info.get("version"),
                )
            )
            seen_paths.add(".")
        _collect_declared_modules(
            repo_path=repo_path,
            current_rel=Path("."),
            inherited_group=root_info.get("group_id"),
            inherited_version=root_info.get("version"),
            modules=modules,
            seen_paths=seen_paths,
            diagnostics=diagnostics,
        )
    else:
        artifact_id = root_info.get("artifact_id") or repo_path.name
        modules.append(
            MavenModule(
                module_id=_slug(artifact_id),
                artifact_id=artifact_id,
                path=".",
                pom_path="pom.xml",
                group_id=root_info.get("group_id"),
                version=root_info.get("version"),
            )
        )

    build_system = {
        "type": "maven",
        "root_pom": "pom.xml",
        "modules": [module.__dict__ for module in modules],
    }
    return build_system, modules, diagnostics


def _collect_declared_modules(
    repo_path: Path,
    current_rel: Path,
    inherited_group: str | None,
    inherited_version: str | None,
    modules: list[MavenModule],
    seen_paths: set[str],
    diagnostics: list[dict],
) -> None:
    current_pom = repo_path / current_rel / "pom.xml"
    current_info = _parse_pom(current_pom) if current_pom.exists() else {}
    for declared in current_info.get("modules") or []:
        module_rel = (repo_path / current_rel / declared).resolve().relative_to(repo_path.resolve())
        rel_posix = module_rel.as_posix()
        pom_path = repo_path / module_rel / "pom.xml"
        info = _parse_pom(pom_path) if pom_path.exists() else {}
        artifact_id = info.get("artifact_id") or Path(declared).name
        module_id = _unique_module_id(_slug(artifact_id), rel_posix, seen_paths)
        if rel_posix not in seen_paths:
            seen_paths.add(rel_posix)
            modules.append(
                MavenModule(
                    module_id=module_id,
                    artifact_id=artifact_id,
                    path=rel_posix,
                    pom_path=(module_rel / "pom.xml").as_posix(),
                    group_id=info.get("group_id") or inherited_group,
                    version=info.get("version") or inherited_version,
                )
            )
        if not pom_path.exists():
            diagnostics.append(
                {
                    "level": "warning",
                    "code": "maven_module_path_missing",
                    "module": artifact_id,
                    "path": rel_posix,
                    "message": f"Declared Maven module has no pom.xml: {rel_posix}",
                }
            )
            continue
        _collect_declared_modules(
            repo_path=repo_path,
            current_rel=module_rel,
            inherited_group=info.get("group_id") or inherited_group,
            inherited_version=info.get("version") or inherited_version,
            modules=modules,
            seen_paths=seen_paths,
            diagnostics=diagnostics,
        )


def _parse_pom(path: Path) -> dict:
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return {}

    ns = ""
    if root.tag.startswith("{"):
        ns = root.tag.split("}", 1)[0] + "}"

    def text(name: str) -> str | None:
        node = root.find(f"{ns}{name}")
        return node.text.strip() if node is not None and node.text else None

    modules = []
    modules_node = root.find(f"{ns}modules")
    if modules_node is not None:
        for child in modules_node.findall(f"{ns}module"):
            if child.text:
                modules.append(child.text.strip())

    return {
        "group_id": text("groupId"),
        "artifact_id": text("artifactId"),
        "version": text("version"),
        "modules": modules,
    }


def _slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-") or "module"


def _unique_module_id(base: str, rel_path: str, existing_paths: set[str]) -> str:
    if rel_path not in existing_paths:
        return base
    return _slug(f"{rel_path}-{base}")


def _posix(value) -> str:
    return Path(value).as_posix()
