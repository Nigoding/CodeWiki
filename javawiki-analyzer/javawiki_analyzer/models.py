from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class MavenModule:
    module_id: str
    artifact_id: str
    path: str
    pom_path: str
    group_id: str | None = None
    version: str | None = None


@dataclass
class JavaField:
    name: str
    type: str
    resolved_type: str | None = None
    annotations: list[str] = field(default_factory=list)
    injection: str | None = None
    remote_client_kind: str | None = None


@dataclass
class JavaMethod:
    name: str
    signature: str
    return_type: str | None
    annotations: list[str] = field(default_factory=list)
    calls: list[dict[str, Any]] = field(default_factory=list)
    span: dict[str, int] | None = None
    remote_endpoints: list[dict[str, Any]] = field(default_factory=list)


@dataclass
class JavaComponent:
    component_id: str
    language: str
    kind: str
    package: str
    qualified_name: str
    simple_name: str
    file_path: str
    span: dict[str, int]
    annotations: list[str] = field(default_factory=list)
    modifiers: list[str] = field(default_factory=list)
    stereotype: str | None = None
    extends: str | None = None
    implements: list[str] = field(default_factory=list)
    imports: list[str] = field(default_factory=list)
    fields: list[JavaField] = field(default_factory=list)
    methods: list[JavaMethod] = field(default_factory=list)
    entry_points: list[dict[str, Any]] = field(default_factory=list)
    source_code: str = ""
    maven_module: str | None = None
    diagnostics: list[dict[str, Any]] = field(default_factory=list)
    remote_endpoints: list[dict[str, Any]] = field(default_factory=list)


@dataclass
class Dependency:
    from_component_id: str
    to_component_id: str
    kind: str
    detail: str | None = None

