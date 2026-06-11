# 流程章节模板

本模板规定模块文档中“场景流程详解”的写法。流程生成必须基于 artifacts 或源码证据，但正文默认不输出源码行号、源码片段或完整证据链，只保留业务解释和轻量实现引用。

## 1. 可展开为场景流程的条件

只有满足以下条件时，才可以把入口展开为正式业务场景流程：

- 存在明确入口证据：后端入口来自 `components/*.json.entry_points`、定时/消息/事件监听方法，或前端 flow 来自 `page_flows.json`。
- 存在调用链证据：后端链路来自 `methods[].calls[].resolved_component`、`dependencies.json` 或源码中的实际方法调用；前端链路来自 `page_flows.json.steps[]` 与 `api_index.json.api_calls[]`。
- 存在业务含义证据：流程名称、触发点、关键数据、返回处理或外部依赖至少能从前端页面标题、bizId、API 函数名、后端方法名、配置或源码分支中确认。
- Mermaid 图中的 participant、消息和边必须与证据一致。

不满足条件时按以下方式降级：

- 没有入口证据：不生成该流程。
- 只有入口、没有调用链：写成场景入口说明，不扩展为端到端流程。
- 没有异常、状态流转、条件分支或配置证据：不写对应分支；可在“待确认点”写“当前源码未确认异常/分支处理”。
- 前端 `confidence == low`：不展开为正式业务流程，仅在“待确认点”中列为低置信度线索。
- 只有业务常识、命名猜测或接口路径相似：不得生成流程；标注为待确认点。

## 2. 场景流程结构

每个核心场景使用以下结构：

```markdown
### 场景：<场景名称>

#### 业务目的

<说明该场景解决什么业务问题，用户或上游系统为什么需要它。>

#### 触发条件

- 前端页面：<page_title，若存在>
- 用户动作：<button/event/lifecycle，若存在>
- 后端入口：`<ControllerOrListener>#<method>`
- 系统事件 / 消息 / 定时任务：<若存在>

#### 主流程

1. <前端或上游发起业务请求。>
2. <当前模块识别业务意图并完成基础处理。>
3. <当前模块调用业务主机或内部模块获取 / 提交数据。>
4. <当前模块聚合、转换、筛选或判断返回结果。>
5. <当前模块返回业务结果给前端或上游。>

#### 流程图

​```mermaid
sequenceDiagram
    autonumber
    participant User as 用户 / 上游系统
    participant Page as 前端页面
    participant Module as 当前模块
    participant Host as 业务主机 / 下游系统

    User->>Page: 触发业务动作
    Page->>Module: 发起业务请求
    Module->>Host: 获取 / 提交业务数据
    Host-->>Module: 返回业务结果
    Module-->>Page: 返回处理结果
    Page-->>User: 展示结果或提示
​```

#### 分支与异常路径

- 成功路径：<只写有证据的结果>
- 业务拒绝：<只写有证据的拒绝条件或返回处理>
- 外部业务主机失败：<只写有证据的失败处理>
- 空结果：<只写有证据的空结果处理>
- 超时 / 降级：<只写有证据的超时、降级或兜底>
- 前端错误提示：<只写前端 artifacts 能确认的提示或页面处理>

#### 输出结果

- 成功：<业务结果>
- 失败：<业务结果>
- 拒绝：<业务结果>
- 空结果：<业务结果>
- 待确认：<证据不足时才写>

#### 相关实现

- 入口：`<ControllerOrListener>#<method>`
- 编排服务：`<Service>#<method>`
- 外部依赖：`<Client>#<method>`
- 前端 API：`<apiFunction>()`
```

## 3. 前端驱动场景

如果当前 flow 来自前端 `page_flows.json` 并经 [frontend-backend-alignment.md](frontend-backend-alignment.md) 对齐到本模块的后端 endpoint：

- 场景名称优先使用前端页面标题、业务动作或 `bizId` 表示的业务含义。
- “触发条件”必须写页面、触发点、前端 API 和对齐方式。
- `confidence == high` 的 flow 可以展开为核心场景。
- `confidence == medium` 的 flow 可以展开，但要在“待确认点”说明触发点来自静态识别。
- `confidence == low` 的 flow 不展开。
- 已在前端场景中展开过的后端 endpoint 不重复生成独立后端场景。

## 4. 后端入口场景

没有前端 analysis 或前端未覆盖时，从重要后端入口中挑选场景：

- REST Controller：适合描述外部调用或页面请求承接。
- 定时任务：适合描述批处理、同步、对账、状态刷新等后台场景。
- 消息消费者：适合描述异步处理、事件驱动或状态推进。
- 事件监听：适合描述内部业务事件后的补充处理。

仅当调用链能说明业务目的时才展开；否则写入“核心业务场景”表格即可。

## 5. 编写硬约束

- 不输出源码行号、源码片段或逐步证据表，除非用户明确要求代码级追踪。
- 不输出完整接口清单、参数表、交易码列表或 DTO 字段大全。
- 不把参数校验、枚举清单、字段转换包装成业务规则。
- 分支与异常路径只写会改变当前场景走向的判断、拒绝、降级、兜底或错误提示。
- 没有证据的状态、规则、外部系统、错误提示或前端行为，一律不写成确定事实。
- 相关实现只列轻量引用：类名、方法名、组件名或前端 API 函数名。

## 6. 数据来源映射

| 模板字段 | 取自 |
|----------|------|
| 后端入口 | `components/*.json.entry_points` 或监听/定时方法 |
| 后端调用链 | `methods[].calls[].resolved_component`、`dependencies.json` 或源码方法调用 |
| 远程依赖 | `remote_endpoints`、远程客户端字段或 [remote-call-recognition.md](remote-call-recognition.md) fallback |
| 前端触发点 / 页面 / bizId | `page_flows.json.flows[i].{trigger, page_title, biz_id, component_file}` |
| 前端 API 函数定义 | `api_index.json.api_calls[<api_call_id>].{function_name, defined_in, method, path, confidence}` |
| 前后端对齐方式 | 工作内存 `matched_flows[i].steps[j].match_level` + `match_reason`（见 [frontend-backend-alignment.md](frontend-backend-alignment.md) §3） |
| 分支和异常 | 源码分支、异常抛出、配置项、前端错误处理或 analyzer 暴露的错误路径 |
