# 分析产物 Schema

`javawiki-analyzer` 输出 JSON artifacts 给 agent 使用。分析器只提供确定性代码事实和候选模块，不负责模型调用和 Markdown 文档生成。

## 目录结构

```text
<analysis-dir>/
├── analysis.json
├── candidate_modules.json
├── module_tree.json
├── processing_order.json
├── component_index.json
├── dependencies.json
├── modules/
│   └── <module_id>.json
└── components/
    └── <component_artifact>.json
```

优先从 `analysis.json.artifacts` 解析文件名，不要硬编码路径。

## `analysis.json`

分析入口文件。

重要字段：

- `schema_version`：当前为 `1.1`。`1.0` 仍可读取，但缺少 `remote_endpoints` / `external_systems` 字段，远程调用文档质量降级；遇到旧版本应建议用户重新运行 analyzer。
- `analysis_id`：本次分析 ID。
- `generated_at`：分析生成时间。
- `source`：输入类型、原始输入、本地解析路径、仓库名和 Git 信息。
- `source.submodules`：Git submodule 状态列表；没有 submodule 时为空数组。
- `build_system`：Maven 根 POM 和递归 Maven module 列表。
- `artifacts`：其他 artifact 的相对路径。
- `summary`：语言、Java 文件数、组件数、候选模块数、最终模块数、REST endpoint 数、远程出站端点数（`total_remote_endpoints`）、远程客户端组件数（`total_remote_clients`）。
- `aggregation`：当前模块树来源。`rule_fallback` 表示 analyzer 规则兜底；`agent_llm` 表示 agent 已执行模型聚合。
- `diagnostics`：解析异常、跳过文件和分析告警。

`build_system.modules` 表示物理 Maven 结构，即使最终文档模块树按业务域聚合，也应保留这部分信息作为证据。

`source.submodules` 中每个元素通常包含：

- `path`
- `url`
- `name`
- `commit`
- `initialized`
- `status`：如 `ok`、`missing`、`commit_mismatch`、`conflict`、`unknown`。

与 submodule 相关的 `diagnostics.code` 可能包括：

- `submodule_skipped`
- `submodule_init_required`
- `submodule_missing`
- `submodule_shallow_update_failed`
- `submodule_commit_mismatch`
- `submodule_conflict`
- `submodule_update_failed`
- `maven_module_path_missing`
- `java_source_not_found`
- `java_source_outside_maven_modules`

如果这些诊断存在，说明 Java 组件、依赖图、候选模块或最终文档可能不完整。

## `candidate_modules.json`

模型聚合阶段的主要输入。它不是最终模块树。

重要字段：

- `schema_version`
- `purpose`
- `candidates`
- `component_candidates`
- `dependency_summary`
- `notes`

`candidates` 中每个候选模块包含：

- `candidate_id`
- `name`
- `source`：如 `maven`、`package_context`、`technical_layer`、`entrypoint_group`。
- `confidence`
- `rationale`
- `maven_module`
- `maven_module_ids`
- `packages`
- `spring_stereotypes`
- `component_ids`
- `entry_points_count`

聚合时应综合多个候选来源，不要只采用单一技术层候选。

## `module_tree.json`

最终文档模块树，采用扁平 map。

示例：

```json
{
  "root_modules": ["congomall"],
  "modules": {
    "cart": {
      "module_id": "cart",
      "name": "Cart",
      "kind": "parent",
      "doc_path": "modules/cart.md",
      "maven_module": null,
      "packages": [],
      "component_ids": [],
      "child_module_ids": ["cart-api", "cart-application", "cart-domain", "cart-persistence"],
      "depends_on_module_ids": []
    }
  }
}
```

规则：

- `module_id` 是稳定引用键。
- `doc_path` 是文档输出路径，必须唯一。
- `child_module_ids` 为空表示叶子模块。
- `component_ids` 只应出现在叶子模块中。
- `depends_on_module_ids` 表示最终模块级依赖。

如果 agent 执行模型聚合，必须重写 `module_tree.json`。

## `processing_order.json`

定义自底向上的模块文档生成顺序。

```json
{
  "steps": [
    {"module_id": "cart-api", "kind": "leaf"},
    {"module_id": "cart", "kind": "parent"}
  ],
  "overview_after_modules": true
}
```

`overview.md` 始终在所有模块之后生成。

## `component_index.json`

紧凑组件索引，用于查找和筛选，不包含完整源码。

常用字段：

- `component_id`
- `language`
- `kind`
- `package`
- `qualified_name`
- `simple_name`
- `file_path`
- `span`
- `annotations`
- `stereotype`
- `module_id`
- `artifact_path`

需要源码、字段、方法、入口或诊断时，再读取 `artifact_path` 指向的 `components/*.json`。

## `components/*.json`

单个 Java 组件详情。

常用字段：

- `component_id`
- `language`
- `kind`
- `package`
- `qualified_name`
- `simple_name`
- `file_path`
- `span`
- `annotations`
- `stereotype`（新增取值：`remote_client_feign`、`remote_client_http_exchange`）
- `extends`
- `implements`
- `fields`：每个字段含 `remote_client_kind`（`rest_template` / `web_client` / `okhttp_client` / `http_client` / `rest_client`），命中表示该字段是远程 HTTP 客户端
- `methods`：每个方法的 `calls[]` 元素含 `target`、`kind`、`line`、`snippet`、`resolved_component`；额外含 `remote_endpoints`（见下）
- `entry_points`
- `remote_endpoints`：聚合视图。每项 `{kind, http_method, target_method, client_field, url, literal_source, line, via_method}`：
  - `kind`：`rest_template` / `web_client` / `okhttp_client` / `http_client` / `rest_client` / `remote_client_feign` / `remote_client_http_exchange`
  - `url`：从字符串字面量或 `${...}` 占位符提取；可能为 `null`（无法解析）
  - `literal_source`：`string` / `placeholder` / `annotation` / `null`
- `maven_module`
- `source_code`
- `diagnostics`

组件可以表示 Java `class`、`interface`、`enum`、`record` 或 `annotation`。

## `modules/*.json`（新增字段）

除原有字段外，叶子模块 JSON 还包含：

- `remote_endpoints`：聚合本模块所有组件的远程出站端点，每项含 `component_id`、`qualified_name`、`file_path` 用于跳转回组件。
- `external_systems`：去重后的目标主机或占位符变量名列表（如 `api.partner.com`、`partner.gateway.url`）。

## `dependencies.json`（新增字段）

`component_dependencies` 中可能出现 `kind == "remote_call"`，`to_component_id` 形如 `external::<host>` 或 `external::<kind>:unresolved`，表示一次远程出站调用。
新增顶层 `external_module_dependencies` 列出每个模块到外部系统的聚合边。

## `modules/*.json`

单个最终模块文档的主入口。

常用字段：

- `module_id`
- `name`
- `kind`
- `doc_path`
- `maven_module`
- `packages`
- `spring_stereotypes`
- `components`
- `parent_module_id`
- `child_module_ids`
- `internal_dependencies`
- `external_dependencies`
- `entry_points`
- `important_files`
- `diagnostics`

叶子模块优先使用本文件，再按需读取 `components/*.json`。父模块使用本文件、子模块摘要和 `dependencies.json`。

## `dependencies.json`

全局依赖图。

常用字段：

- `component_dependencies`
- `module_dependencies`

依赖 `kind` 可能包括：

- `constructor_injection`
- `field_injection`
- `field_type`
- `method_call`
- `object_creation`
- `extends`
- `implements`

模型聚合时可使用 `component_dependencies` 判断业务上下文耦合；文档总览和父模块图优先使用最终 `module_dependencies`。
