from __future__ import annotations

from pathlib import Path
import re
import xml.etree.ElementTree as ET

from javawiki_analyzer.models import MavenModule


SUBPROJECT_SCAN_MAX_DEPTH = 3
SUBPROJECT_SCAN_EXCLUDE_NAMES = {
    ".git", ".idea", ".vscode", ".gradle", ".mvn",
    "node_modules", "target", "build", "out", "dist",
}


def scan_maven_modules(repo_path: Path) -> tuple[dict, list[MavenModule], list[dict]]:
    diagnostics: list[dict] = []
    modules: list[MavenModule] = []
    seen_paths: set[str] = set()
    seen_module_ids: set[str] = set()

    root_pom = repo_path / "pom.xml"
    if root_pom.exists():
        _scan_subproject(
            repo_path=repo_path,
            root_rel=Path("."),
            modules=modules,
            seen_paths=seen_paths,
            seen_module_ids=seen_module_ids,
            diagnostics=diagnostics,
        )
        return (
            {"type": "maven", "root_pom": "pom.xml", "modules": [m.__dict__ for m in modules]},
            modules,
            diagnostics,
        )

    discovered = _discover_subproject_root_dirs(repo_path)
    if not discovered:
        diagnostics.append(
            {
                "level": "warning",
                "code": "no_pom_found",
                "message": "No pom.xml found in the repository or any scanned subdirectory; treating the repository as a single Java source root.",
            }
        )
        synthetic = MavenModule("root", repo_path.name, ".", "pom.xml")
        return (
            {"type": "maven", "root_pom": None, "modules": [synthetic.__dict__]},
            [synthetic],
            diagnostics,
        )

    discovered_paths = [p.as_posix() for p in discovered]
    diagnostics.append(
        {
            "level": "info",
            "code": "no_root_pom_discovered_subprojects",
            "discovered_subproject_roots": discovered_paths,
            "message": (
                f"Repository has no root pom.xml; auto-discovered {len(discovered)} subproject root(s) "
                f"and treated each as an independent Maven project: {', '.join(discovered_paths)}."
            ),
        }
    )

    for root_rel in discovered:
        _scan_subproject(
            repo_path=repo_path,
            root_rel=root_rel,
            modules=modules,
            seen_paths=seen_paths,
            seen_module_ids=seen_module_ids,
            diagnostics=diagnostics,
        )

    return (
        {
            "type": "maven",
            "root_pom": None,
            "discovered_subproject_roots": discovered_paths,
            "modules": [m.__dict__ for m in modules],
        },
        modules,
        diagnostics,
    )


def _discover_subproject_root_dirs(repo_path: Path) -> list[Path]:
    """BFS subdirectories until a pom.xml is found; record that directory and stop descending."""
    discovered: list[Path] = []

    def walk(current: Path, depth: int) -> None:
        if depth >= SUBPROJECT_SCAN_MAX_DEPTH:
            return
        try:
            entries = sorted(current.iterdir())
        except (PermissionError, OSError):
            return
        for entry in entries:
            if not entry.is_dir():
                continue
            if entry.name in SUBPROJECT_SCAN_EXCLUDE_NAMES or entry.name.startswith("."):
                continue
            if (entry / "pom.xml").exists():
                discovered.append(entry.relative_to(repo_path))
            else:
                walk(entry, depth + 1)

    walk(repo_path, 0)
    return discovered


def _scan_subproject(
    repo_path: Path,
    root_rel: Path,
    modules: list[MavenModule],
    seen_paths: set[str],
    seen_module_ids: set[str],
    diagnostics: list[dict],
) -> None:
    """Treat repo_path/root_rel as a Maven root and walk its declared module tree."""
    rel_posix = "." if root_rel == Path(".") else root_rel.as_posix()
    pom_path_rel = "pom.xml" if rel_posix == "." else f"{rel_posix}/pom.xml"
    pom_file = repo_path / root_rel / "pom.xml"
    info = _parse_pom(pom_file) if pom_file.exists() else {}

    artifact_id = info.get("artifact_id") or (
        repo_path.name if rel_posix == "." else Path(rel_posix).name
    )
    has_src = (repo_path / root_rel / "src" / "main" / "java").exists()
    declared = info.get("modules") or []

    if rel_posix not in seen_paths and (has_src or not declared):
        module_id = _make_unique_id(_slug(artifact_id), seen_module_ids)
        seen_paths.add(rel_posix)
        seen_module_ids.add(module_id)
        modules.append(
            MavenModule(
                module_id=module_id,
                artifact_id=artifact_id,
                path=rel_posix,
                pom_path=pom_path_rel,
                group_id=info.get("group_id"),
                version=info.get("version"),
            )
        )

    if declared:
        _collect_declared_modules(
            repo_path=repo_path,
            current_rel=root_rel,
            inherited_group=info.get("group_id"),
            inherited_version=info.get("version"),
            modules=modules,
            seen_paths=seen_paths,
            seen_module_ids=seen_module_ids,
            diagnostics=diagnostics,
        )


def _collect_declared_modules(
    repo_path: Path,
    current_rel: Path,
    inherited_group: str | None,
    inherited_version: str | None,
    modules: list[MavenModule],
    seen_paths: set[str],
    seen_module_ids: set[str],
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
        if rel_posix not in seen_paths:
            module_id = _make_unique_id(_slug(artifact_id), seen_module_ids)
            seen_paths.add(rel_posix)
            seen_module_ids.add(module_id)
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
            seen_module_ids=seen_module_ids,
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


def _make_unique_id(base: str, existing_ids: set[str]) -> str:
    if base not in existing_ids:
        return base
    index = 2
    while f"{base}-{index}" in existing_ids:
        index += 1
    return f"{base}-{index}"
