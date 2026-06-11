# 模块文档模板

模块文档面向 Java 维护者，目标是让读者能**沿着真实代码读懂业务流程**——既能看到架构骨架，也能从入口跟踪到每个调用点的行号。只写 artifacts 或源码片段能够支持的内容。

## 通用规则

- 写入 `module_tree.json` 中的模块 `doc_path`。
- 每个 `processing_order.json.steps[].module_id` 都必须有独立模块文档；`overview.md` 不能替代模块文档。
- 一级标题使用模块 `name`。
- 链接使用相对当前 Markdown 文件的路径。
- 这是业务说明文档，不是 API 文档。不要输出交易码列表、完整接口表格、请求/响应参数清单或接口手册式内容。
- 文档篇幅优先给业务概述、业务流程和模块依赖；代码结构、组件列表和配置只写支撑理解所需的内容。
- 提到 Java 类型时使用 `qualified_name`；类标题下**必须**紧跟 `**File**: <file_path>`。
- 流程关键步骤**必须**能在源码中找到对应行号；引用格式：`<file_path>:Lstart-Lend`。
- 提到接口、依赖、Maven 模块或组件时，必须能在 `modules/*.json`、`components/*.json`、`dependencies.json` 或 `analysis.json.build_system` 中找到依据。
- 远程调用必须有明确 URL（或 `${...}` 占位符 / `<unresolved>` 标记），见 [remote-call-recognition.md](remote-call-recognition.md)。
- 不杜撰：源码或 artifacts 中找不到的字段、方法、URL、异常类、配置项一律不写。

## 叶子模块

固定章节顺序（不得调整、不得跳过；某些章节确实无内容时写"无（说明原因）"）：

```markdown
# 模块名称

## 1. 业务概述（Business Overview）

说明本模块在 Java/Maven/Spring 系统中的职责、边界和不负责的内容。1-3 段，每段 ≤ 4 行。

## 2. 业务流程（Business Flows）

本节是模块文档重点。优先写前端驱动流程和重要后端入口，按 [flow-section-template.md](flow-section-template.md) 展开；接口路径只作为流程证据出现，不输出完整 API 清单。

当存在前端 analysis 时，先写“前端业务流程”，再写未被前端覆盖的重要后端入口。已在前端流程中出现过的后端 endpoint 不重复展开，正文交叉引用即可。

业务流程必须配 Mermaid 图。端到端调用链使用 `sequenceDiagram`；纯业务状态流、审批流、阶段流可使用 `flowchart TD`。

## 3. 业务规则（Business Rules）

仅当源码、配置或前端流程中能明确证明规则存在且有业务价值时输出。没有明确业务规则时写“无明确业务规则证据”，不要把参数校验、DTO 字段或枚举清单包装成业务规则。

## 4. 模块结构（Module Structure）

​```mermaid
graph TB
    subgraph Presentation
        Controller1["XxxController"]
    end
    subgraph Business
        Service1["XxxService"]
    end
    subgraph External
        RemoteClient1["XxxFeignClient"] --> H1["api.partner.com"]
    end
    Controller1 --> Service1
    Service1 --> RemoteClient1
​```

- 节点取自本模块组件 `simple_name`；外部节点取自 `modules/*.json.external_systems`。
- 边的依据是 `dependencies.json` 中本模块组件的 `component_dependencies`。

## 5. 核心组件（Core Components）

按 stereotype 分小节：Controllers / Services / Remote Clients / Configurations / Domain Models / Schedulers / Listeners。

每个组件用四级标题，紧跟 `**File**`：

### XxxController

**File**: `<file_path>`

- **职责**：1-2 句。
- **业务入口**（若是 Controller / Feign）：只列与核心业务流程相关的入口，说明其业务作用；不要输出完整接口表。
- **Key Operations**（若是 Service）：
  - `doSomething()` — `<file>:Lxx-Lyy`：一句话作用。
- **Key Fields**：列重要字段（含远程客户端字段、`@Value` 配置字段）；DTO 类型字段不必逐个列出。
- **继承/实现**：`extends X` / `implements Y`。

## 6. 模块依赖（Module Dependencies）

按 [dependency-analysis-rules.md](dependency-analysis-rules.md) 输出。依赖描述必须尽量细化到“模块名 → 服务类 → 具体方法 → 调用目的”，并配 Mermaid `graph TB` 或 `graph LR`。不要只根据 `pom.xml` 推断依赖关系。

## 7. 集成点（Integration Points）

| 外部系统 | 调用类 | 调用方法 | 用途 | 来源 |
|----------|--------|----------|------|------|
| ${inventory.platform.url} | `OrderService` | `freezeStock()` | 下单前冻结库存 | analyzer |

- 数据来自 `modules/*.json.remote_endpoints`；若该字段为空但源码可见远程调用，按 [remote-call-recognition.md](remote-call-recognition.md) fallback 填写并标"来源 = fallback"。
- URL 为占位符时附一行"配置项：`<key>` 见 `application.yml`/`bootstrap.yml`"。
- 无远程调用时写"本模块无对外远程调用"。

## 8. 配置与错误处理（Configuration & Error Handling）

- 列出会影响业务行为的配置项，例如开关、阈值、超时、重试、降级。
- 列出核心错误处理路径、错误码含义和业务拒绝场景。
- 不输出纯技术参数清单。

## 9. 维护注意事项（Maintenance Notes）

- 事务、扩展点、并发风险。
- 远程调用注意：超时、重试、降级链路。
- analyzer 覆盖不足的地方（如使用 fallback 识别、`<unresolved>` URL 数量、`source_code` 截断）。
- Schema 版本警告（若读到 `schema_version < 1.1` 必须在此说明）。

## 10. 相关文档（Related Documentation）

列出本模块依赖或被依赖的兄弟模块文档链接（相对路径）。
```

## 父模块

固定结构：

```markdown
# 模块名称

## 1. 概述

说明该业务域或技术域的整体职责。

## 2. 子模块导航

| 模块 | 职责 | 文档 |
|------|------|------|
| XxxApi | 对外接口层 | [link](api.md) |

## 3. 架构关系

​```mermaid
graph TB
    subgraph 本模块
        sub1["api"]
        sub2["application"]
    end
    External["partner.gateway"]
    sub1 --> sub2 --> External
​```

子模块作为节点；外部系统取自所有叶子子模块 `external_systems` 的并集。

## 4. 跨模块业务流（Cross-Module Flow）

对**至少一个**跨越多个子模块的端到端业务流程，按 [flow-section-template.md](flow-section-template.md) 的重要入口骨架展开（含 sequenceDiagram + 编号步骤 + 行号 + alt）。不要写"API → Application → Domain → Persistence"这种空泛分层描述。

## 5. 依赖边界

总结允许的依赖方向（如 api → application → domain）和实际观察到的依赖方向；标注违反方向的边（如有）。

## 6. 横切关注点

公共模型、配置、基础设施、远程调用统一约定、消息、缓存、安全或任务约定。
```

父模块必须保持概述级别，但**跨模块业务流必须真实可追溯**——这是父模块文档对读者最大的价值。

## Java/Spring 关注重点

按优先级：

1. 业务流程：前端页面触发、Controller 承接、Service 编排、远程调用和返回处理。
2. 业务规则：有源码或配置证据的状态流转、阈值、开关、准入/拒绝条件。
3. 模块依赖：实际服务类和方法调用，含跨模块调用。
4. 远程客户端：`@FeignClient`、`@HttpExchange`、`RestTemplate`、`WebClient` 等及目标系统。
5. MQ producer/consumer、`@Scheduled` 任务、`@EventListener`。
6. 配置和错误处理对业务行为的影响。

## Mermaid 图

仅在图能提高理解时使用。强制场景：

- **业务流程**：每个重要流程必出 `sequenceDiagram` 或 `flowchart TD`。
- **模块结构**：模块组件数 ≥ 2 必出 `graph TB`。
- **模块依赖**：跨模块依赖必须尽量出 `graph LR` 或 `graph TB`，边上标注服务类/方法。
- **领域实体多于 3 个**：可加 `classDiagram` 展示字段与关系。

参考 [mermaid-rules.md](mermaid-rules.md)。
