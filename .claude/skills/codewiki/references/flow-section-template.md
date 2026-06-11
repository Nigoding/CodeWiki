# 流程章节固定模板

本模板规定**重要入口**（critical entry point）流程章节的写法。叶子模块文档的"主要流程"小节，对每个被标记为重要的入口必须严格按以下骨架展开，确保流程描述既详细又可追溯到源码。

非重要入口可只列调用链 bullet（见 §3"次要入口简写"）。

## 1. 何谓重要入口

由 `SKILL.md §4「入口流程清单生成」` 阶段标记。任一条件成立即视为重要：

- 调用深度 ≥ 3（沿 `methods[].calls.resolved_component` 递归）。
- 调用链命中 `remote_call` 边（依赖 `dependencies.json` 中 `kind == "remote_call"` 或组件 `remote_endpoints` 非空）。
- 跨越 ≥ 2 个 Spring stereotype（如 controller → service → remote_client_feign）。
- 入口本身是定时任务（`@Scheduled`）、消息消费者（`@RabbitListener`/`@KafkaListener`）或事件监听（`@EventListener`）。

## 1.5 流程可生成条件

只有满足以下条件时，才可以把入口展开为正式业务流程：

- 必须存在明确入口证据：后端入口来自 `components/*.json.entry_points`、定时/消息/事件监听方法，或前端 flow 来自 `page_flows.json`。
- 必须存在调用链证据：后端链路来自 `methods[].calls[].resolved_component`、`dependencies.json` 或源码中的实际方法调用；前端链路来自 `page_flows.json.steps[]` 与 `api_index.json.api_calls[]`。
- 必须存在业务含义证据：流程名称、触发点、关键字段或返回处理至少能从前端页面标题、bizId、API 函数名、后端方法名、配置或源码分支中确认。
- Mermaid 图中的 participant、消息和边必须与上述证据一致。

不满足条件时按以下方式降级：

- 没有入口证据：不生成该流程。
- 只有入口、没有调用链：写成入口说明或次要入口简写，不得扩展为端到端流程。
- 没有异常、状态流转、条件分支或配置证据：不画对应 `alt` 分支；可写“当前源码未确认异常/分支处理”。
- 前端 `confidence == low`：不展开为正式业务流程，仅在维护注意事项或分析说明中列为低置信度线索。
- 只有业务常识、命名猜测或接口路径相似：不得生成流程；必须标注为待确认点。

## 2. 重要入口流程章节骨架

每个重要入口产出一个三级标题段落：

```markdown
### <HTTP方法> <路径> — <一句话作用>

**入口**：`<qualified_name>#<method>()` — `<file_path>:Lstart-Lend`

#### 时序图

​```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Controller as <ControllerSimpleName>
    participant Service as <ServiceSimpleName>
    participant RemoteClient as <RemoteClientSimpleName>
    participant External as <外部中台主机或服务名>

    Client->>Controller: <HTTP方法> <路径>\n请求体: <主要字段>
    Controller->>Service: <方法名>(<关键参数>)
    Service->>RemoteClient: <方法名>(...)
    RemoteClient->>External: <HTTP方法> <URL>
    alt 成功
        External-->>RemoteClient: 200 <响应摘要>
        RemoteClient-->>Service: <返回类型>
        Service-->>Controller: <返回类型>
        Controller-->>Client: 200 <ResultVo>
    else 远程失败 / 超时
        External-->>RemoteClient: <错误码 / Timeout>
        RemoteClient-->>Service: 抛 <ExceptionClass>
        Service-->>Controller: 抛 <ExceptionClass>
        Controller-->>Client: <ErrorResponse>(<错误码>)
    end
​```

#### 步骤详解

1. **`<qualified_name>#<method>()`** — `<file_path>:Lstart-Lend`
   - 参数校验：<参数名> 经过 `@Valid` / 手工断言（引用具体行号）。
   - 调用：`<次级类>#<方法>()` 在 `<file_path>:Lxx`。
2. **`<次级类>#<方法>()`** — `<file_path>:Lxx-Lyy`
   - 业务逻辑要点：<装配/转换/分支判断>。
   - 远程调用：`<RemoteClient>#<方法>()` 命中 `<HTTP方法> <URL>`（来源 `components/<name>.json` → `remote_endpoints[<idx>]`）。
3. **`<RemoteClient>#<方法>()`** — `<file_path>:Lxx-Lyy`
   - 目标：`<外部主机或占位符>`。
   - 请求关键字段：<列举主要请求体或 query 参数，引用源码片段中的字段名>。
   - 响应处理：<解析路径，引用源码>。

#### 异常与降级

- **<异常类>**：触发条件 + 当前处理（重试 / 降级 / 抛出），引用 `<file_path>:Lxx`。
- **超时**：如配置可见（`@Value("${...}")` / `HttpClientConfig`），列出超时时间。
- **降级链路**：若有 `@HystrixCommand` / `@CircuitBreaker` / try-catch 兜底逻辑，给出落点。
```

## 2.1 前端驱动变体（当存在 vuewiki 分析产物时）

如果当前 flow 来自前端 `page_flows.json` 并经 [frontend-backend-alignment.md](frontend-backend-alignment.md) 对齐到本模块的某个后端 endpoint（`matched_flows` 集合），章节标题改用前端业务名称，并在时序图最左侧补 `User` / `Page` participant。骨架如下：

```markdown
### <page_title> · <trigger 中文释义> (`<flow_id>`)

- **业务标识**：`bizId=<biz_id>`
- **页面**：`<page_title>`
- **触发点**：<lifecycle / event 中文释义>（前端 `<component_file>`）
- **前端 API**：`<function_name>()` 定义于 `<defined_in>`
- **对齐方式**：<exact / placeholder_normalized / suffix_match>（confidence: <high/medium/low>）
- **后端入口**：`<qualified_name>#<method>()` — `<file_path>:Lstart-Lend`

#### 时序图

​```mermaid
sequenceDiagram
    autonumber
    participant User
    participant Page as <Page.vue 简称>
    participant FrontApi as <api.js 简称>
    participant Controller as <ControllerSimpleName>
    participant Service as <ServiceSimpleName>
    participant RemoteClient as <RemoteClientSimpleName>
    participant External as <外部中台主机或服务名>

    User->>Page: <触发动作释义>
    Page->>FrontApi: <function_name>(<关键参数>)
    FrontApi->>Controller: <HTTP方法> <路径>\n请求体: <主要字段>
    Controller->>Service: <方法名>(<关键参数>)
    Service->>RemoteClient: <方法名>(...)
    RemoteClient->>External: <HTTP方法> <URL>
    alt 成功
        External-->>RemoteClient: 200 <响应摘要>
        RemoteClient-->>Service: <返回类型>
        Service-->>Controller: <返回类型>
        Controller-->>FrontApi: 200 <ResultVo>
        FrontApi-->>Page: <前端处理: setData / 跳转>
        Page-->>User: <UI 反馈>
    else 远程失败 / 业务拒绝
        External-->>RemoteClient: <错误码>
        RemoteClient-->>Service: 抛 <ExceptionClass>
        Service-->>Controller: 抛 <ExceptionClass>
        Controller-->>FrontApi: <ErrorResponse>
        FrontApi-->>Page: <前端处理: toast / 留在原页>
        Page-->>User: <错误提示>
    end
​```

#### 步骤详解

1. **<触发动作>** — 前端 `<component_file>:Lxx`
   - <事件 / 生命周期来源描述>
2. **`<function_name>()`** — 前端 `<defined_in>:Lxx`
   - 构造请求体 `<主要字段>`，调用 `request({ url: '<路径>', method: '<HTTP方法>' })`。
3. **`<qualified_name>#<method>()`** — `<file_path>:Lstart-Lend`
   - 后续步骤同 §2 重要入口的"步骤详解"段，按 `methods[].calls[].resolved_component` 递归展开。
```

**前端步骤的写法约束**：

- 前端文件行号若 `page_flows.json` 未给出，写 `(行号未知，前端 analyzer 未提供 span)`。
- `function_name`、`method`、`path` 必须从 `page_flows.json.steps[]` 与 `api_index.json.api_calls[]` 取，不杜撰。
- 若 `match_level == suffix_match`，在"对齐方式"行后补一行：`> 注意：后端 path 是前端 path 的尾段匹配，疑似存在网关前缀 \`<前缀>\`，请确认。`
- `confidence == medium` 的触发点（template 事件、watch）在"步骤详解"第 1 步加 "（基于 template 静态识别）"；`confidence == low` 的 step 整条 flow 不展开（仅在维护注意事项列出）。

## 3. 次要入口简写

对未被标记为重要的入口，使用单段 bullet 列表：

```markdown
### <HTTP方法> <路径> — <一句话作用>

- 入口：`<qualified_name>#<method>()` — `<file_path>:Lstart-Lend`
- 调用链：`<Controller>#<m>` → `<Service>#<m>` → `<下游>`
- 远程调用：无 / `<HTTP方法> <URL>`
```

## 4. 编写硬约束

- **步骤数 ≥ 3**：流程必须能拆出至少 3 个语义步骤；不足 3 步说明它不是重要入口，应改用简写。
- **每步必须含行号**：`file_path:Lstart-Lend` 是硬要求；如果分析产物的 `span` 缺失，需在该步显式标注 "行号未知（analyzer 未提供）"，不得直接省略。
- **类名/方法名一致**：所有引用必须出现在 `components/*.json` 的 `qualified_name`、`methods[].name`、`fields[].name` 中。任何 prompt 推断出来但 artifacts 找不到的名字必须删除。
- **远程调用必须列 URL 或显式标记 `<unresolved>`**：禁止用 "调用外部服务" 这类无信息描述。
- **`alt` 分支基于证据生成**：只有源码、配置或 artifacts 能证明异常 / 超时 / 业务拒绝分支时才画；没有证据时不画分支，并写明“当前源码未确认异常/分支处理”。
- **sequenceDiagram 参与者命名**：使用 Spring 组件的 `simple_name`，不使用完整 qualified_name（防止 Mermaid 解析错误）。如需消歧加 `participant svc as UserService`。
- **不杜撰**：源码或 artifacts 中找不到的字段、方法、URL、异常类一律不写。可写"行为未在源码中显式标注，需结合下游文档确认"。

## 5. 完整示例

```markdown
### POST /api/order/create — 创建订单并冻结库存

**入口**：`com.acme.order.controller.OrderController#createOrder()` — `src/main/java/com/acme/order/controller/OrderController.java:48-72`

#### 时序图

​```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Controller as OrderController
    participant Service as OrderService
    participant Stock as StockFeignClient
    participant External as inventory.platform.url

    Client->>Controller: POST /api/order/create\nCreateOrderReq{skuId, qty}
    Controller->>Service: createOrder(req, userId)
    Service->>Stock: freezeStock(skuId, qty)
    Stock->>External: POST /inventory/freeze
    alt 库存充足
        External-->>Stock: 200 {token}
        Stock-->>Service: FreezeResp(token)
        Service-->>Controller: OrderVo
        Controller-->>Client: 200 OrderVo
    else 库存不足
        External-->>Stock: 409 INSUFFICIENT
        Stock-->>Service: 抛 StockNotEnoughException
        Service-->>Controller: 抛 BizException(40901)
        Controller-->>Client: 409 {code:40901, msg:"库存不足"}
    end
​```

#### 步骤详解

1. **`OrderController#createOrder()`** — `src/main/java/com/acme/order/controller/OrderController.java:48-72`
   - `@Valid CreateOrderReq req` 校验非空字段（`skuId`, `qty`）。
   - 从 `@AuthenticationPrincipal` 取得 `userId`。
   - 调用 `orderService.createOrder(req, userId)` — line 64。
2. **`OrderService#createOrder()`** — `src/main/java/com/acme/order/service/OrderService.java:91-138`
   - 调 `stockFeignClient.freezeStock(req.skuId, req.qty)` 冻结库存 — line 102。
   - 成功后构造 `OrderVo` 返回。
3. **`StockFeignClient#freezeStock()`** — `src/main/java/com/acme/order/remote/StockFeignClient.java:22-29`
   - `@PostExchange("/inventory/freeze")`，目标主机来自 `${inventory.platform.url}`（`application.yml`）。
   - 请求体：`FreezeReq{skuId, qty}`。

#### 异常与降级

- **`StockNotEnoughException`**：`StockFeignClient` 的 `@ExceptionHandler` 转换为 `BizException(40901)` — `src/main/java/com/acme/order/remote/StockFeignErrorDecoder.java:34`。
- **超时**：`spring.cloud.openfeign.client.config.stock.connect-timeout=2000` — `application.yml:42`。
- **降级**：未实现 `fallback`；超时直接抛 `RetryableException` 由全局 `@ControllerAdvice` 兜底。
```

## 6. 数据来源映射

| 模板字段 | 取自 |
|----------|------|
| 入口 `qualified_name#method` | `components/*.json` → `entry_points[i].class` + `entry_points[i].method` |
| 入口行号 | `methods[i].span.start_line/end_line` |
| 调用链下一步 | `methods[i].calls[j].resolved_component`（递归到 `components/*.json`） |
| 调用点行号 | `methods[i].calls[j].line` |
| 远程 URL | `remote_endpoints[i].url`（来自 component 顶层或 method 内嵌） |
| 远程 HTTP 方法 | `remote_endpoints[i].http_method` |
| 远程客户端类名 | `remote_endpoints[i].client_field` 解析为字段类型，或 `stereotype == remote_client_feign` 的组件 |
| 异常 | 源码 `throw new X(...)` 或方法签名 `throws X`；若 `methods[i].calls` 中没暴露，需读 `source_code` 字段定位 |
| 前端触发点 / 页面 / bizId | `page_flows.json.flows[i].{trigger, page_title, biz_id, component_file}` |
| 前端 API 函数定义 | `api_index.json.api_calls[<api_call_id>].{function_name, defined_in, method, path, confidence}` |
| 前后端对齐方式 | 工作内存 `matched_flows[i].steps[j].match_level` + `match_reason`（见 [frontend-backend-alignment.md](frontend-backend-alignment.md) §3） |

