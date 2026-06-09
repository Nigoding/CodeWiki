from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


@dataclass
class ResolvedSource:
    source_type: str
    input: str
    path: Path
    repo_name: str
    cleanup_path: Path | None = None
    submodules: list[dict] = field(default_factory=list)
    diagnostics: list[dict] = field(default_factory=list)


def resolve_source(
    source: str,
    output_dir: Path,
    keep_clone: bool,
    submodules: str = "auto",
    init_submodules: bool = False,
) -> ResolvedSource:
    if _is_remote(source):
        return _clone_remote(source, output_dir, keep_clone, submodules)

    path = Path(source).expanduser().resolve()
    if not path.exists() or not path.is_dir():
        raise ValueError(f"Local repository path does not exist or is not a directory: {path}")
    submodule_records, diagnostics = _handle_submodules(
        repo_path=path,
        mode=submodules,
        allow_init=init_submodules,
    )
    return ResolvedSource("local", source, path, path.name, submodules=submodule_records, diagnostics=diagnostics)


def cleanup_source(resolved: ResolvedSource) -> None:
    if resolved.cleanup_path and resolved.cleanup_path.exists():
        shutil.rmtree(resolved.cleanup_path, ignore_errors=True)


def read_git_info(repo_path: Path) -> dict[str, str | None]:
    def run_git(args: list[str]) -> str | None:
        try:
            result = subprocess.run(
                ["git", "-C", str(repo_path), *args],
                capture_output=True,
                text=True,
                check=True,
                timeout=10,
            )
            return result.stdout.strip() or None
        except Exception:
            return None

    return {
        "commit": run_git(["rev-parse", "HEAD"]),
        "branch": run_git(["rev-parse", "--abbrev-ref", "HEAD"]),
        "remote_url": run_git(["remote", "get-url", "origin"]),
    }


def _is_remote(source: str) -> bool:
    return source.startswith(("https://", "http://", "git@"))


def _clone_remote(source: str, output_dir: Path, keep_clone: bool, submodules: str) -> ResolvedSource:
    repo_name = _repo_name_from_url(source)
    if keep_clone:
        clone_dir = output_dir.resolve() / "_source" / repo_name
        clone_dir.parent.mkdir(parents=True, exist_ok=True)
        if clone_dir.exists():
            if not (clone_dir / ".git").exists():
                raise ValueError(f"Clone destination exists but is not a git repository: {clone_dir}")
            subprocess.run(["git", "-C", str(clone_dir), "fetch", "--depth", "1"], check=False)
        else:
            subprocess.run(["git", "clone", "--depth", "1", source, str(clone_dir)], check=True)
        cleanup_path = None
    else:
        temp_dir = Path(tempfile.mkdtemp(prefix="javawiki_"))
        clone_dir = temp_dir / repo_name
        subprocess.run(["git", "clone", "--depth", "1", source, str(clone_dir)], check=True)
        cleanup_path = temp_dir

    submodule_records, diagnostics = _handle_submodules(
        repo_path=clone_dir,
        mode=submodules,
        allow_init=submodules in {"auto", "recursive"},
    )
    return ResolvedSource(
        "remote",
        source,
        clone_dir.resolve(),
        repo_name,
        cleanup_path,
        submodules=submodule_records,
        diagnostics=diagnostics,
    )


def _repo_name_from_url(url: str) -> str:
    tail = url.rstrip("/").split("/")[-1]
    tail = tail[:-4] if tail.endswith(".git") else tail
    safe = re.sub(r"[^A-Za-z0-9_.-]+", "-", tail).strip("-")
    return safe or "repository"


def _handle_submodules(repo_path: Path, mode: str, allow_init: bool) -> tuple[list[dict], list[dict]]:
    diagnostics: list[dict] = []
    if mode not in {"none", "auto", "recursive"}:
        raise ValueError(f"Unsupported submodule mode: {mode}")

    gitmodules = repo_path / ".gitmodules"
    if not gitmodules.exists():
        return [], diagnostics

    if mode == "none":
        records = _read_submodule_records(repo_path)
        diagnostics.append(
            {
                "level": "warning",
                "code": "submodule_skipped",
                "message": "Repository declares Git submodules, but submodule handling is disabled.",
            }
        )
        return records, diagnostics

    if allow_init:
        diagnostics.extend(_update_submodules(repo_path, shallow=True))
    else:
        diagnostics.append(
            {
                "level": "warning",
                "code": "submodule_init_required",
                "message": (
                    "Repository declares Git submodules. Local repository analysis does not initialize "
                    "submodules unless --init-submodules is provided."
                ),
            }
        )

    records = _read_submodule_records(repo_path)
    for record in records:
        if not record.get("initialized"):
            diagnostics.append(
                {
                    "level": "warning",
                    "code": "submodule_missing",
                    "path": record.get("path"),
                    "url": record.get("url"),
                    "message": f"Git submodule is not initialized: {record.get('path')}",
                }
            )
        elif record.get("status") == "commit_mismatch":
            diagnostics.append(
                {
                    "level": "warning",
                    "code": "submodule_commit_mismatch",
                    "path": record.get("path"),
                    "message": f"Git submodule working tree is not at the recorded commit: {record.get('path')}",
                }
            )
        elif record.get("status") == "conflict":
            diagnostics.append(
                {
                    "level": "error",
                    "code": "submodule_conflict",
                    "path": record.get("path"),
                    "message": f"Git submodule has merge conflicts: {record.get('path')}",
                }
            )
    return records, diagnostics


def _update_submodules(repo_path: Path, shallow: bool) -> list[dict]:
    diagnostics: list[dict] = []
    sync_command = ["git", "-C", str(repo_path), "submodule", "sync", "--recursive"]
    update_command = ["git", "-C", str(repo_path), "submodule", "update", "--init", "--recursive"]

    sync_error = _run_submodule_command(sync_command)
    if sync_error:
        return [sync_error]

    if shallow:
        shallow_error = _run_submodule_command([*update_command, "--depth", "1"])
        if not shallow_error:
            return diagnostics
        diagnostics.append(
            {
                "level": "warning",
                "code": "submodule_shallow_update_failed",
                "command": "git submodule update --init --recursive --depth 1",
                "message": "Shallow submodule update failed; retrying without --depth.",
                "error": shallow_error["error"],
            }
        )

    full_error = _run_submodule_command(update_command)
    if full_error:
        diagnostics.append(full_error)
    return diagnostics


def _run_submodule_command(command: list[str]) -> dict | None:
    try:
        subprocess.run(command, capture_output=True, text=True, check=True, timeout=300)
        return None
    except subprocess.CalledProcessError as exc:
        return {
            "level": "error",
            "code": "submodule_update_failed",
            "command": " ".join(command[:4]),
            "message": "Failed to initialize or update Git submodules.",
            "error": (exc.stderr or exc.stdout or str(exc)).strip(),
        }
    except Exception as exc:
        return {
            "level": "error",
            "code": "submodule_update_failed",
            "command": " ".join(command[:4]),
            "message": "Failed to initialize or update Git submodules.",
            "error": str(exc),
        }


def _read_submodule_records(repo_path: Path) -> list[dict]:
    definitions = _read_gitmodule_definitions(repo_path)
    statuses = _read_submodule_status(repo_path)
    records: list[dict] = []

    paths = sorted(set(definitions.keys()) | set(statuses.keys()))
    for path in paths:
        definition = definitions.get(path, {})
        status = statuses.get(path, {})
        records.append(
            {
                "path": path,
                "url": definition.get("url"),
                "name": definition.get("name"),
                "commit": status.get("commit"),
                "initialized": status.get("initialized", False),
                "status": status.get("status", "unknown"),
            }
        )
    return records


def _read_gitmodule_definitions(repo_path: Path) -> dict[str, dict]:
    gitmodules = repo_path / ".gitmodules"
    try:
        result = subprocess.run(
            [
                "git",
                "config",
                "--file",
                str(gitmodules),
                "--get-regexp",
                r"^submodule\..*\.(path|url)$",
            ],
            capture_output=True,
            text=True,
            check=True,
            timeout=10,
        )
    except Exception:
        return {}

    by_name: dict[str, dict] = {}
    for line in result.stdout.splitlines():
        key, _, value = line.partition(" ")
        if not value:
            continue
        name_and_field = key.removeprefix("submodule.")
        name, _, field_name = name_and_field.rpartition(".")
        by_name.setdefault(name, {"name": name})[field_name] = value.strip()

    return {
        entry["path"]: entry
        for entry in by_name.values()
        if entry.get("path")
    }


def _read_submodule_status(repo_path: Path) -> dict[str, dict]:
    try:
        result = subprocess.run(
            ["git", "-C", str(repo_path), "submodule", "status", "--recursive"],
            capture_output=True,
            text=True,
            check=True,
            timeout=30,
        )
    except Exception:
        return {}

    statuses: dict[str, dict] = {}
    for line in result.stdout.splitlines():
        if not line:
            continue
        marker = line[0]
        parts = line[1:].strip().split()
        if len(parts) < 2:
            continue
        commit, path = parts[0], parts[1]
        statuses[path] = {
            "commit": commit,
            "initialized": marker != "-",
            "status": _submodule_status_name(marker),
        }
    return statuses


def _submodule_status_name(marker: str) -> str:
    if marker == "-":
        return "missing"
    if marker == "+":
        return "commit_mismatch"
    if marker == "U":
        return "conflict"
    if marker == " ":
        return "ok"
    return "unknown"
