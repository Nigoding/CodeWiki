# CodeWiki MCP 契约

本文定义文档生成 skill 所依赖的分析接口。MCP 负责返回最终稳定的模块划分；生成 Agent 不修改模块树。

## `analyze_repository`

完整分析仓库并递归拆分模块，直到满足深度、规模或 token 预算限制。

### 输入

```json
{
  "repo_path": "/absolute/path/to/repository",
  "include_patterns": ["*.py", "*.java"],
  "exclude_patterns": ["*test*", "*spec*"],
  "max_depth": 3,
  "max_token_per_module": 36000,
  "force_recluster": false
}
```

除 `repo_path` 外均为可选参数。默认行为必须包含递归聚类，不应要求生成 Agent 动态拆分模块。

### 输出

```json
{
  "analysis_id": "stable-analysis-id",
  "repo_name": "example",
  "component_index": {
    "src/auth.py::AuthService": {
      "name": "AuthService",
      "file_path": "src/auth.py",
      "kind": "class",
      "language": "python",
      "depends_on": ["src/db.py::UserRepository"],
      "summary": "Optional deterministic summary"
    }
  },
  "module_tree": {
    "auth": {
      "module_id": "auth",
      "name": "Authentication",
      "doc_path": "modules/auth.md",
      "components": ["src/auth.py::AuthService"],
      "children": {}
    }
  },
  "processing_order": ["auth"],
  "diagnostics": []
}
```

### 约束

- `module_tree` 是最终结构，所有递归聚类在响应前完成。
- `module_id` 在一次分析结果中保持稳定。
- `doc_path` 必须唯一、相对输出目录且不包含 `..`。
- `processing_order` 使用 `module_id`，覆盖每个模块一次。
- 子模块必须排在父模块之前。
- `processing_order` 不包含仓库根总览。
- 初始响应优先返回紧凑索引，不应默认携带所有完整源码。
- 聚类失败、解析失败和跳过的文件必须写入 `diagnostics`。

## `get_module_context`

返回生成单个模块文档所需的紧凑上下文。

### 输入

```json
{
  "analysis_id": "stable-analysis-id",
  "module_id": "auth",
  "include_source": true
}
```

### 输出

```json
{
  "module": {
    "module_id": "auth",
    "name": "Authentication",
    "doc_path": "modules/auth.md",
    "components": ["src/auth.py::AuthService"],
    "children": []
  },
  "components": {},
  "internal_dependencies": [],
  "external_dependencies": [],
  "entry_points": [],
  "diagnostics": []
}
```

组件源码允许截断，但必须明确标记。父模块上下文应重点返回直接子模块之间的关系，而不是重复所有后代源码。

## `get_components`

按需批量获取完整组件信息。

### 输入

```json
{
  "analysis_id": "stable-analysis-id",
  "component_ids": [
    "src/auth.py::AuthService",
    "src/db.py::UserRepository"
  ]
}
```

### 输出要求

每个组件至少包含：

- `component_id`
- `file_path`
- `kind`
- `source_code`
- `depends_on`
- 可用时包含签名、调用关系和源码位置

未知组件必须作为逐项错误返回，不能使整批请求失败。
