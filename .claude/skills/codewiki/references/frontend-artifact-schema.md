# 前端分析产物 Schema

`vuewiki-analyzer` 输出 Vue2 前端仓库分析 artifacts，用于生成页面业务流程文档，并和后端 `javawiki-analyzer` 的 `entry_points` 对齐。

## 目录结构

```text
<frontend-analysis-dir>/
├── analysis.json
├── module_tree.json
├── page_index.json
├── component_index.json
├── api_index.json
├── page_flows.json
├── backend_api_usage.json
├── modules/
├── pages/
└── components/
```

## `analysis.json`

重要字段：

- `schema_version`
- `source`
- `framework`：当前目标是 Vue2。
- `entry_config`：通常为 `owl.config.js`。
- `artifacts`
- `summary`
- `diagnostics`

## `module_tree.json`

业务模块来自 `owl.config.js -> bundlerConfig.pages` 的一级 key。

模块字段：

- `module_id`
- `name`
- `kind`：`business_module` 或 `shared`
- `root_path`
- `declared_in`
- `page_ids`
- `api_call_ids`

## `page_index.json`

页面来自 `owl.config.js -> bundlerConfig.pages.<module>.<page>`。

页面字段：

- `page_id`
- `module_id`
- `page_name`
- `title`
- `biz_id`
- `entry_file`：优先为 `src/<module>/<page>/app.vue`
- `component_ids`
- `flow_ids`
- `component_edges`

## `component_index.json`

组件从页面入口 `app.vue` 开始递归解析。

组件字段：

- `component_id`
- `page_id`
- `module_id`
- `file_path`
- `role`：`entry` 或 `child`
- `imports`
- `registered_components`
- `template_components`
- `template_events`
- `lifecycle_hooks`
- `method_names`
- `watch_names`
- `api_call_ids`

## `api_index.json`

前端 API 定义。

API 字段：

- `api_call_id`
- `module_id`
- `function_name`
- `method`
- `path`
- `defined_in`
- `confidence`

## `page_flows.json`

页面核心调用流程。

流程字段：

- `flow_id`
- `module_id`
- `page_id`
- `page_title`
- `biz_id`
- `component_id`
- `component_file`
- `component_role`
- `trigger`
- `steps`
- `confidence`

`steps` 中的 `api_call` 包含：

- `order`
- `api_call_id`
- `function_name`
- `method`
- `path`
- `confidence`

## `backend_api_usage.json`

用于和后端接口对齐的扁平清单。

字段：

- `method`
- `path`
- `frontend_module_id`
- `page_id`
- `page_title`
- `component_id`
- `component_file`
- `trigger`
- `api_call_id`
- `confidence`

前后端融合时，用 `method + path` 匹配后端 `modules/*.json` 或 `components/*.json` 中的 `entry_points`。
