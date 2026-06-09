from __future__ import annotations

from pathlib import Path
import re

from javawiki_analyzer.models import MavenModule


def scan_java_files(
    repo_path: Path,
    modules: list[MavenModule],
    include_patterns: list[str],
    exclude_patterns: list[str],
) -> tuple[list[tuple[Path, MavenModule]], list[MavenModule], list[dict]]:
    results: list[tuple[Path, MavenModule]] = []
    seen: set[str] = set()
    extra_modules: dict[str, MavenModule] = {}
    diagnostics: list[dict] = []
    for module in modules:
        module_root = repo_path if module.path == "." else repo_path / module.path
        if not module_root.exists():
            continue
        for pattern in include_patterns or ["src/main/java/**/*.java"]:
            for file_path in module_root.glob(pattern):
                _add_file(results, seen, file_path, repo_path, module, exclude_patterns)

    initial_count = len(results)
    for file_path in repo_path.rglob("*.java"):
        if _excluded(file_path, repo_path, exclude_patterns):
            continue
        key = file_path.resolve().as_posix()
        if key in seen:
            continue
        module = _module_for_unmapped_file(repo_path, file_path, modules, extra_modules)
        _add_file(results, seen, file_path, repo_path, module, exclude_patterns)

    if len(results) > initial_count:
        diagnostics.append(
            {
                "level": "info",
                "code": "java_source_outside_maven_modules",
                "message": "Found Java files outside declared Maven module source roots; added synthetic source modules.",
                "extra_java_files": len(results) - initial_count,
                "extra_modules": [module.__dict__ for module in sorted(extra_modules.values(), key=lambda item: item.path)],
            }
        )

    return sorted(results, key=lambda item: item[0].as_posix()), list(extra_modules.values()), diagnostics


def _add_file(
    results: list[tuple[Path, MavenModule]],
    seen: set[str],
    file_path: Path,
    repo_path: Path,
    module: MavenModule,
    exclude_patterns: list[str],
) -> None:
    if not file_path.is_file() or _excluded(file_path, repo_path, exclude_patterns):
        return
    key = file_path.resolve().as_posix()
    if key not in seen:
        seen.add(key)
        results.append((file_path, module))


def _fallback_module(repo_path: Path, modules: list[MavenModule]) -> MavenModule:
    if len(modules) == 1:
        return modules[0]
    root_module = next((module for module in modules if module.path == "."), None)
    if root_module:
        return root_module
    return MavenModule("root", repo_path.name, ".", "pom.xml")


def _module_for_unmapped_file(
    repo_path: Path,
    file_path: Path,
    modules: list[MavenModule],
    extra_modules: dict[str, MavenModule],
) -> MavenModule:
    existing = _nearest_existing_module(repo_path, file_path, modules)
    if existing:
        return existing

    source_root = _source_root_for_file(repo_path, file_path)
    if source_root not in extra_modules:
        artifact_id = repo_path.name if source_root == "." else source_root.split("/")[-1]
        module_id = _unique_extra_module_id(source_root, {module.module_id for module in [*modules, *extra_modules.values()]})
        pom_path = f"{source_root}/pom.xml" if source_root != "." else "pom.xml"
        extra_modules[source_root] = MavenModule(
            module_id=module_id,
            artifact_id=artifact_id,
            path=source_root,
            pom_path=pom_path,
        )
    return extra_modules[source_root]


def _nearest_existing_module(repo_path: Path, file_path: Path, modules: list[MavenModule]) -> MavenModule | None:
    rel = file_path.relative_to(repo_path).as_posix()
    candidates = []
    for module in modules:
        module_path = "" if module.path == "." else module.path.strip("/")
        if module_path and (rel == module_path or rel.startswith(module_path + "/")):
            candidates.append((len(module_path), module))
    if not candidates:
        return None
    return sorted(candidates, key=lambda item: item[0], reverse=True)[0][1]


def _source_root_for_file(repo_path: Path, file_path: Path) -> str:
    rel_parts = file_path.relative_to(repo_path).parts
    marker = ("src", "main", "java")
    for index in range(0, len(rel_parts) - len(marker) + 1):
        if rel_parts[index : index + len(marker)] == marker:
            prefix = rel_parts[:index]
            return Path(*prefix).as_posix() if prefix else "."
    return rel_parts[0] if len(rel_parts) > 1 else "."


def _unique_extra_module_id(source_root: str, existing_ids: set[str]) -> str:
    base = _slug(source_root if source_root != "." else "root")
    candidate = base
    index = 2
    while candidate in existing_ids:
        candidate = f"{base}-{index}"
        index += 1
    return candidate


def _slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-") or "module"


def _excluded(path: Path, repo_path: Path, patterns: list[str]) -> bool:
    try:
        rel = path.relative_to(repo_path)
    except ValueError:
        rel = path
    rel_posix = rel.as_posix()
    wrapped = f"/{rel_posix}"
    for pattern in patterns:
        base = pattern.rstrip("/**")
        if rel.match(pattern) or rel_posix.startswith(base):
            return True
        if base in {".git", "target", "src/test"} and f"/{base}/" in f"{wrapped}/":
            return True
    return False
