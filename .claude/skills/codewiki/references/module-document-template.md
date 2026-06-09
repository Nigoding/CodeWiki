# 模块文档模板

模块文档面向 Java 维护者，目标是让读者能**沿着真实代码读懂业务流程**——既能看到架构骨架，也能从入口跟踪到每个调用点的行号。只写 artifacts 或源码片段能够支持的内容。

## 通用规则

- 写入 `module_tree.json` 中的模块 `doc_path`。
- 一级标题使用模块 `name`。
- 链接使用相对当前 Markdown 文件的路径。
- 提到 Java 类型时使用 `qualified_name`；类标题下**必须**紧跟 `**File**: <file_path>`。
- 流程关键步骤**必须**能在源码中找到对应行号；引用格式：`<file_path>:Lstart-Lend`。
- 提到接口、依赖、Maven 模块或组件时，必须能在 `modules/*.json`、`components/*.json`、`dependencies.json` 或 `analysis.json.build_system` 中找到依据。
- 远程调用必须有明确 URL（或 `${...}` 占位符 / `<unresolved>` 标记），见 [remote-call-recognition.md](remote-call-recognition.md)。
- 不杜撰：源码或 artifacts 中找不到的字段、方法、URL、异常类、配置项一律不写。

## 叶子模块

固定章节顺序（不得调整、不得跳过；某些章节确实无内容时写"无（说明原因）"）：

```markdown
# 模块名称

## 1. 概述（Introduction）

说明本模块在 Java/Maven/Spring 系统中的职责、边界和不负责的内容。1-3 段，每段 ≤ 4 行。

## 2. 架构总览（Architecture Overview）

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

## 3. 核心组件（Core Components）

按 stereotype 分小节：Controllers / Services / Remote Clients / Configurations / Domain Models / Schedulers / Listeners。

每个组件用四级标题，紧跟 `**File**`：

### XxxController

**File**: `<file_path>`

- **职责**：1-2 句。
- **Key Endpoints**（若是 Controller / Feign）：
  | HTTP | 路径 | 方法 | 一句话作用 |
  |------|------|------|------------|
  | POST | /api/x/create | `createX()` | 创建 X 实例 |
- **Key Operations**（若是 Service）：
  - `doSomething()` — `<file>:Lxx-Lyy`：一句话作用。
- **Key Fields**：列重要字段（含远程客户端字段、`@Value` 配置字段）；DTO 类型字段不必逐个列出。
- **继承/实现**：`extends X` / `implements Y`。

## 4. 数据流（Data Flow）

对每个被标记为"重要入口"的 endpoint，严格按 [flow-section-template.md](flow-section-template.md) 展开（含 sequenceDiagram、编号步骤、行号锚点、alt 异常分支）。

对非重要入口，按 flow-section-template §3 的简写规则列调用链 bullet。

## 5. 集成点（Integration Points）

| HTTP | URL | 调用类 | 调用方法 | 客户端类型 | 来源 |
|------|-----|--------|----------|------------|------|
| POST | ${inventory.platform.url}/inventory/freeze | `OrderService` | `freezeStock()` | feign | analyzer |
| GET  | http://api.partner.com/v1/user/{id} | `UserService` | `getForObject()` | rest_template | analyzer |

- 数据来自 `modules/*.json.remote_endpoints`；若该字段为空但源码可见远程调用，按 [remote-call-recognition.md](remote-call-recognition.md) fallback 填写并标"来源 = fallback"。
- URL 为占位符时附一行"配置项：`<key>` 见 `application.yml`/`bootstrap.yml`"。
- 无远程调用时写"本模块无对外远程调用"。

## 6. 配置（Configuration）

- 列出 `@ConfigurationProperties` 类、`@Value` 注入的配置 key、关键的 `application.yml` 配置项（含远程超时、重试、降级）。
- 引用格式：`<key>` — `<file_path>:Lxx`（若可读取到 yml）或仅列 key + 用途。
- 无配置类时写"无独立配置"。

## 7. 错误处理（Error Handling）

- 自定义异常类清单（继承自 RuntimeException 或框架异常）。
- 全局异常处理：`@ControllerAdvice` / `@ExceptionHandler` 落点 + 行号。
- 错误码表：
  | 错误码 | 含义 | 触发场景 | 源码位置 |
  |--------|------|----------|----------|
- 无统一错误处理时写"无独立错误处理（依赖上层 ControllerAdvice）"。

## 8. 依赖关系（Dependencies）

- **内部依赖**：列出本模块依赖的其他文档模块 + 关系类型（`constructor_injection` / `method_call` / `extends` / `implements`）。数据源 `modules/*.json.external_dependencies`。
- **外部系统依赖**：列出本模块调用的外部主机 / 服务，数据源 `modules/*.json.external_systems`。

## 9. 维护注意事项（Maintenance Notes）

- 事务、扩展点、并发风险。
- 远程调用注意：超时、重试、降级、降级降级链路。
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

1. `RestController` / `Controller` 对外入口及其完整调用链。
2. 远程客户端（`@FeignClient`、`@HttpExchange`、`RestTemplate` 字段、`WebClient` 字段、`HttpClient` 字段）及目标 URL。
3. `Service` 之间的调用链（含跨模块）。
4. 领域聚合、值对象、领域服务（如有）。
5. `Configuration`、`@Bean`、`@ConfigurationProperties` 对运行时行为的影响（含远程超时/重试）。
6. MQ producer/consumer、`@Scheduled` 任务、`@EventListener`。
7. 模块依赖方向是否符合预期。

## Mermaid 图

仅在图能提高理解时使用。强制场景：

- **§2 架构总览**：模块组件数 ≥ 2 必出 `graph TB`。
- **§4 数据流**：每个"重要入口"必出 `sequenceDiagram`（异常用 `alt`）。
- **领域实体多于 3 个**：可加 `classDiagram` 展示字段与关系。

参考 [mermaid-rules.md](mermaid-rules.md)。
