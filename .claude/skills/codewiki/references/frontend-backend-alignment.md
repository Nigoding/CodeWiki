# 前后端调用对齐规则

文档生成时如果同时拥有 `javawiki-analyzer`（后端）和 `vuewiki-analyzer`（前端）的分析产物，必须执行前后端 endpoint 对齐：以前端 `page_flows.json` 中的用户业务入口为主线，沿着前端调用顺序映射到对应的后端 endpoint，再下钻后端调用链。这是生成"以业务为线索"文档的关键步骤。

本文档定义对齐键、规范化规则、命中分类和未匹配处理策略。

## 1. 输入数据

**前端（vuewiki-analyzer）：**

- `page_flows.json.flows[]`：每条 flow 含 `flow_id`、`page_id`、`page_title`、`biz_id`、`component_id`、`component_file`、`trigger`、`steps[]`。
- `flow.steps[]`：按 `order` 升序排列，类型为 `api_call` 的步骤含 `method`、`path`、`function_name`、`confidence`。
- `backend_api_usage.json.backend_usages[]`：扁平化的 `(method, path, frontend_module_id, page_id, component_id, trigger, confidence)` 列表，便于按 (method, path) 反查。
- `api_index.json.api_calls[]`：前端 API 函数定义，含 `defined_in`（前端文件路径）、`confidence`。

**后端（javawiki-analyzer）：**

- `components/*.json.entry_points[]`：每个 REST endpoint 含 `http_method`、`path`、`class`、`method`、`file_path`、`span`。

## 2. 对齐键：(method, normalized_path)

两边都通过 `(HTTP_method, normalized_path)` 作为 join key。

### 2.1 path 规范化规则

按顺序执行：

1. **统一斜杠**：去掉前后空白与多余斜杠，确保以 `/` 开头，结尾不留 `/`。例：`pension/kyc/submit/` → `/pension/kyc/submit`。
2. **占位符归一**：把所有路径变量统一替换成 `{}`：
   - `{id}` → `{}`
   - `:id`（Vue Router / Express 风格） → `{}`
   - `${id}`（前端模板字符串变量） → `{}`
3. **字面量段归一**（仅当后端对应段是占位符时启用）：前端拼接出的字面量段（纯数字、纯标识符）若对应的后端段是 `{}`，也归一化为 `{}`。例：
   - 前端：`GET /users/123` → 候选 keys: `[GET /users/123, GET /users/{}]`
   - 后端：`GET /users/{id}` → 规范化为 `GET /users/{}`
   - 匹配：通过 `GET /users/{}`。
4. **大小写**：HTTP method 强制大写比较；path 段按原样比较（保留大小写，因为 URL path 区分大小写）。

### 2.2 网关前缀剥离

业务网关（如 Spring Cloud Gateway、Kong）通常会在前端 path 前加固定前缀（如 `/api/v1`、`/gateway`），后端代码不含。处理顺序：

1. 用户在 prompt 中显式声明 `frontend_base_path`（如 "前端走网关 `/api/v1`，对齐时请剥离"）→ 在规范化前先剥离该前缀。
2. 前端 `api_index.json` 中能稳定看到所有 path 都以同一前缀开头，且该前缀在后端 endpoint 中完全不存在 → 自动推断为网关前缀，剥离前提示用户确认。
3. 都不满足 → 走 **后缀匹配** 兜底（见 §3.3）。

## 3. 匹配等级

按优先级降序，命中即停止：

### 3.1 exact

规范化后 (method, path) 字符串完全相等。例：
- 前端 `POST /pension/kyc/submit` ↔ 后端 `POST /pension/kyc/submit` → **exact**

### 3.2 placeholder_normalized

规范化中通过占位符归一才匹配上。例：
- 前端 `GET /users/123` ↔ 后端 `GET /users/{id}` → 两边都归一化为 `GET /users/{}` → **placeholder_normalized**

### 3.3 suffix_match（兜底）

仅在 §3.1 / §3.2 都失败、且未声明网关前缀时尝试：把前端 path 与后端 path 都按 `/` 分段，从尾部对齐，要求后端 path 的所有段都能在前端 path 的尾部按顺序匹配（占位符段算通配）。例：
- 前端 `POST /api/v1/pension/kyc/submit`
- 后端 `POST /pension/kyc/submit`
- 尾段 `/pension/kyc/submit` 在前端尾部完整出现 → **suffix_match**，并在文档中标"猜测前缀 `/api/v1` 可能是网关，建议用户确认"。

后缀匹配若同时命中多个后端 endpoint（多义），全部列出，让用户决定。

### 3.4 unmatched

§3.1–§3.3 均未命中。flow 仍在文档中保留（在"前端业务流程"小节单列），并在维护注意事项标注。

## 4. 输出结构（工作内存）

对齐结果产出三个集合，仅作为本次文档生成的工作内存，不落盘：

```
{
  "matched_flows": [
    {
      "flow_id": "pension_kycprocess_stepone_submit",
      "page_title": "KYC流程",
      "biz_id": "4737048",
      "trigger": { "type": "event_handler", "name": "submit", "event": "submit" },
      "frontend_component_file": "src/pension/kycprocess/components/StepOne.vue",
      "steps": [
        {
          "order": 1,
          "method": "POST",
          "path": "/pension/kyc/submit",
          "frontend_function": "submitKycInfo",
          "frontend_defined_in": "src/pension/api/index.js",
          "backend_entry_point": {
            "class": "com.acme.pension.controller.KycController",
            "method": "submit",
            "file_path": "src/main/java/.../KycController.java",
            "span": { "start_line": 42, "end_line": 58 }
          },
          "match_level": "exact",
          "confidence": "high"
        }
      ],
      "overall_confidence": "medium"
    }
  ],
  "frontend_only_flows": [
    {
      "flow_id": "...",
      "reason": "unmatched",
      "steps": [ ... ],
      "note": "调用 /partner-gateway/notify，未在当前后端代码中找到对应 endpoint，可能由其他后端服务或中台实现"
    }
  ],
  "backend_only_endpoints": [
    {
      "entry_point": { "class": "...", "method": "...", "http_method": "POST", "path": "..." },
      "reason": "未被任何前端 flow 引用",
      "importance": "important | minor"
    }
  ]
}
```

### 4.1 overall_confidence 计算

`overall_confidence = min(flow.confidence, min(step.confidence for step in steps), match_level_confidence)`

`match_level_confidence` 表：
- `exact` → `high`
- `placeholder_normalized` → `high`
- `suffix_match` → `medium`

### 4.2 backend_only_endpoints 分级

复用 SKILL.md §4.5 现有的"重要入口"启发式（调用深度 ≥ 3、命中 remote_endpoints、跨 ≥ 2 stereotype、`@Scheduled` / 消息消费者 / 事件监听）。命中任一即标 `important`，其余 `minor`。

## 5. 写文档时如何使用对齐结果

- **叶子模块文档 §4.0「前端业务流程」**：遍历 `matched_flows` 中本模块涉及的 flow（按 frontend module → page → flow_id 排序），每条按 [flow-section-template.md](flow-section-template.md) §2.1「前端驱动变体」展开。
- **叶子模块文档 §4「数据流」**：
  - 被 `matched_flows` 引用的后端 endpoint 不再单独展开，正文交叉引用 `见 §4.0.X`。
  - `backend_only_endpoints.important` 仍按重要入口完整展开 sequenceDiagram。
  - `backend_only_endpoints.minor` 仍按简写 bullet。
- **叶子模块文档 §4.X「前端独立流程」**（仅当模块内有 `frontend_only_flows` 时出现）：列出未对齐的前端流程，明确标"未在当前后端代码中找到对应 endpoint"。
- **overview.md §5「关键流程」**：优先选 `matched_flows.overall_confidence == high` 的端到端路径，每条画一张 sequenceDiagram，跨越 `User → Page.vue → frontend API → Controller → Service → RemoteClient → External`。

## 6. confidence 反映到正文措辞

- `high` → 直接陈述，例如 "点击 StepOne 提交按钮触发 `POST /pension/kyc/submit`"
- `medium` → "可能"措辞，例如 "点击搜索按钮预计触发 `GET /personal-pension/account/list`（基于 template 事件静态分析）"
- `low` → 不展开为流程章节，仅在"维护注意事项"列出，并标明 URL 由前端动态拼接、无法精确对齐

## 7. 反模式

不允许出现：

- 把后端 endpoint 路径强行套到前端"找不到对应"的 flow 上。
- 隐藏 `unmatched` flow 假装前端没调用。
- 用"前端可能调用了某接口"这种无证据描述把 `low` confidence 调用伪装成 `high`。
- 把 suffix_match 当作 exact 写入文档而不标"可能存在网关前缀"。
