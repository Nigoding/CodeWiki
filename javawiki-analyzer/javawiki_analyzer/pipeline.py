from __future__ import annotations

from pathlib import Path
import hashlib

from javawiki_analyzer.artifacts.writer import base_analysis, write_artifacts
from javawiki_analyzer.graph.dependencies import build_dependency_artifact, resolve_component_links
from javawiki_analyzer.java.parser import parse_java_file
from javawiki_analyzer.java.scanner import scan_java_files
from javawiki_analyzer.maven.scanner import scan_maven_modules
from javawiki_analyzer.modules.classifier import build_candidate_modules, classify_modules, processing_order
from javawiki_analyzer.source.resolver import cleanup_source, read_git_info, resolve_source


def run_analysis(
    source: str,
    output_dir: Path,
    include_patterns: list[str],
    exclude_patterns: list[str],
    keep_clone: bool,
    submodules: str = "auto",
    init_submodules: bool = False,
    verbose: bool = False,
) -> Path:
    output_dir = output_dir.expanduser().resolve()
    resolved = resolve_source(source, output_dir, keep_clone, submodules, init_submodules)
    diagnostics: list[dict] = list(resolved.diagnostics)

    try:
        build_system, maven_modules, maven_diagnostics = scan_maven_modules(resolved.path)
        diagnostics.extend(maven_diagnostics)

        java_files, extra_source_modules, java_scan_diagnostics = scan_java_files(
            resolved.path,
            maven_modules,
            include_patterns,
            exclude_patterns,
        )
        if extra_source_modules:
            maven_modules.extend(extra_source_modules)
            build_system["modules"].extend(module.__dict__ for module in extra_source_modules)
        diagnostics.extend(java_scan_diagnostics)
        if not java_files:
            diagnostics.append(
                {
                    "level": "warning",
                    "code": "java_source_not_found",
                    "message": "No Java source files were found. Check repository layout, include patterns, Maven modules, and submodule state.",
                    "include_patterns": include_patterns,
                    "exclude_patterns": exclude_patterns,
                }
            )
        components = []
        for file_path, module in java_files:
            try:
                parsed = parse_java_file(resolved.path, file_path, module)
                components.extend(parsed)
                if verbose:
                    print(f"parsed {file_path.relative_to(resolved.path).as_posix()} ({len(parsed)} components)")
            except Exception as exc:
                diagnostics.append(
                    {
                        "level": "error",
                        "message": f"Failed to parse Java file: {file_path.relative_to(resolved.path).as_posix()}",
                        "error": str(exc),
                    }
                )

        dependencies = resolve_component_links(components)
        candidate_modules = build_candidate_modules(components, dependencies, maven_modules)
        module_tree, component_to_module = classify_modules(components, dependencies)
        order = processing_order(module_tree)
        dependencies_artifact = build_dependency_artifact(dependencies, component_to_module)

        analysis = base_analysis(
            analysis_id=_analysis_id(resolved.repo_name, resolved.path),
            source={
                "type": resolved.source_type,
                "input": resolved.input,
                "resolved_path": str(resolved.path),
                "repo_name": resolved.repo_name,
                "git": read_git_info(resolved.path),
                "submodules": resolved.submodules,
            },
            build_system=build_system,
            summary={
                "language": "java",
                "total_java_files": len(java_files),
                "total_components": len(components),
                "total_modules": len(module_tree["modules"]),
                "total_candidate_modules": len(candidate_modules["candidates"]),
                "total_rest_endpoints": sum(len(component.entry_points) for component in components),
            },
            diagnostics=diagnostics,
        )
        analysis["aggregation"] = {
            "mode": "rule_fallback",
            "candidate_artifact": "candidate_modules.json",
            "final_module_tree_source": "javawiki_analyzer.rules",
            "description": (
                "module_tree.json is a deterministic fallback. "
                "Agents may run an LLM aggregation phase from candidate_modules.json "
                "and overwrite final module artifacts before documentation generation."
            ),
        }

        write_artifacts(
            output_dir=output_dir,
            analysis=analysis,
            module_tree=module_tree,
            order=order,
            candidate_modules=candidate_modules,
            components=components,
            dependencies_artifact=dependencies_artifact,
            component_to_module=component_to_module,
        )
        return output_dir
    finally:
        if not keep_clone:
            cleanup_source(resolved)


def _analysis_id(repo_name: str, repo_path: Path) -> str:
    digest = hashlib.sha1(str(repo_path).encode("utf-8")).hexdigest()[:8]
    return f"{repo_name}-{digest}"
