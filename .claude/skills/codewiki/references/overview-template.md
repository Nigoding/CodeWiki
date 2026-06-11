# 功能全景视图模板

所有模块文档完成后生成 `overview.md`。总览文档用于让读者快速建立整个功能的全景认知，不替代模块文档，不重新展开子模块中的场景流程。

当前后端分析产物与前端分析产物同时存在时，`overview.md` 是一份融合前端页面、后端模块和外部业务主机的功能全景视图，不是后端仓库总览和前端仓库总览的拼接。

生成 `overview.md` 前必须确认所有 `processing_order.json.steps[].module_id` 对应的模块文档已经生成。不得只生成 `overview.md`，也不得把 `overview.md` 当作模块文档的汇总替代品。

## 推荐结构

```markdown
# 功能全景视图

## 1. 功能范围

说明本次文档覆盖的功能范围和分析输入。

- 后端仓库：
- 前端仓库（若有）：
- 后端模块数量：
- 前端页面数量（若有）：
- 外部业务主机数量：
- 文档覆盖边界：

只根据 artifacts、模块文档和用户输入说明范围；不要强行推断主要用户、业务目标或系统愿景。

## 2. 功能上下文图

用一张融合图展示用户 / 上游系统、前端页面、后端模块、内部模块和外部业务主机之间的关系。

​```mermaid
graph TB
    User["用户 / 上游系统"]
    subgraph Frontend["前端业务入口"]
        PageA["页面 A"]
        PageB["页面 B"]
    end
    subgraph Backend["后端业务模块"]
        ModuleA["模块 A"]
        ModuleB["模块 B"]
    end
    subgraph Hosts["外部业务主机 / 中台"]
        HostA["业务主机 A"]
        HostB["业务主机 B"]
    end

    User --> PageA
    User --> PageB
    PageA --> ModuleA
    PageB --> ModuleB
    ModuleA --> ModuleB
    ModuleA --> HostA
    ModuleB --> HostB
​```

生成要求：

- 同时存在前后端产物时，只生成一张融合上下文图。
- 如果存在远程调用，每个外部业务主机必须在图中可见。
- 图中节点和边必须能在 artifacts、源码或已生成模块文档中找到依据。

## 3. 页面到模块映射图

仅在提供 Vue2 前端分析产物时生成本节。

优先使用图表达页面如何进入后端模块：

​```mermaid
graph LR
    PageA["页面 A"]
    ApiA["前端 API A"]
    ModuleA["后端模块 A"]
    DocA["模块文档 A"]

    PageA --> ApiA
    ApiA --> ModuleA
    ModuleA --> DocA
​```

必要时补充轻量表格：

| 页面 | 触发点 | 前端 API | 后端模块 | 匹配状态 | 详情文档 |
|------|--------|----------|----------|----------|----------|
| 页面 A | submit | `submitXxx()` | 模块 A | exact / medium / unmatched | [模块 A](module-a.md) |

要求：

- 使用前端 `page_flows.json`、`api_index.json` 和 `backend_api_usage.json`。
- 不输出完整 API 清单、请求参数表或交易码列表。
- 未匹配项只列代表性路径，并放入第 8 节“分析说明与待确认点”。

没有前端产物时写：本次未提供前端分析产物，无法从页面视角还原业务入口。

## 4. 后端模块协作图

展示后端模块之间的调用关系。只到模块级，必要时边上标注核心服务方法或业务含义。

​```mermaid
graph LR
    ModuleA["模块 A"]
    ModuleB["模块 B"]
    ModuleC["模块 C"]

    ModuleA -->|XxxService#method，业务校验| ModuleB
    ModuleA -->|XxxService#method，补充查询| ModuleC
​```

要求：

- 使用 `dependencies.json.module_dependencies` 和已生成模块文档第 6 节“模块协作与依赖”。
- 不展开完整服务方法清单。
- 不根据 `pom.xml` 单独推断业务依赖。

## 5. 外部业务主机依赖图

展示后端模块依赖哪些外部业务主机，以及这些依赖服务于哪些业务数据或业务用途。

​```mermaid
graph LR
    ModuleA["模块 A"]
    ModuleB["模块 B"]
    HostA["业务主机 A"]
    HostB["业务主机 B"]

    ModuleA -->|获取账户信息| HostA
    ModuleA -->|提交业务申请| HostB
    ModuleB -->|查询处理结果| HostB
​```

必要时补充轻量表格：

| 外部业务主机 | 调用模块 | 获取或提交的数据 | 影响场景 | 失败影响 |
|--------------|----------|------------------|----------|----------|
| 业务主机 A | 模块 A | 账户信息 | 场景 A | 无法展示账户状态 |

要求：

- 数据源为 `modules/*.json.external_systems`、`remote_endpoints`、`dependencies.json.external_module_dependencies` 和模块文档第 5 节。
- 不输出完整 REST 入口清单、RPC 方法清单或参数表。
- 外部业务主机语义无法确认时，放入第 8 节“分析说明与待确认点”。

## 6. 关键业务路径索引

本节只做导航，不展开完整流程。优先选 2-5 条最能帮助读者进入子模块文档的业务路径。

| 业务路径 | 入口页面 / 触发方 | 涉及模块 | 外部依赖 | 详情文档 |
|----------|------------------|----------|----------|----------|
| 路径 A | 页面 A / 上游系统 | 模块 A、模块 B | 业务主机 A | [模块 A](module-a.md) |

选择来源：

- 已生成模块文档中的核心业务场景。
- `matched_flows.overall_confidence == high` 的前端驱动路径。
- 重要后端入口、定时任务、消息消费者或事件监听。

要求：

- 不新增模块文档中没有证据支持的流程。
- 不重新绘制每条路径的完整 sequenceDiagram；详细流程由模块文档负责。

## 7. 文档导航

列出所有模块文档链接（按业务域或模块树排序）。

| 模块 | 主要职责 | 文档 |
|------|----------|------|
| 模块 A | 说明模块职责 | [模块 A](module-a.md) |

若有前端产物，可增加页面导航表：

| 页面 | 前端模块 | 主要后端模块 | 文档 |
|------|----------|--------------|------|
| 页面 A | pension | 模块 A | [模块 A](module-a.md) |

## 8. 分析说明与待确认点

说明分析质量、未对齐区域和需要人工确认的内容。

- 后端 analyzer 诊断（含 `remote_url_unresolved`）。
- 前端 analyzer 诊断（含 `page_entry_not_found`、动态 API path、低置信度 flow）。
- `frontend_only_flows` 中代表性未匹配页面调用。
- `backend_only_endpoints.important` 中代表性未被前端覆盖的后端入口。
- submodule 缺失或源码缺失。
- 模型聚合边界与未确认区域。
- 外部业务主机语义无法从源码确认的地方。
- Schema 版本信息（若后端 schema < 1.1 必须说明远程信息可能不完整）。
```

## 生成规则

- `overview.md` 是功能全景视图，优先使用 Mermaid 图表达关系，不重复模块文档中的场景流程详解。
- 图优先级：功能上下文图 > 页面到模块映射图 > 后端模块协作图 > 外部业务主机依赖图。
- 如果同时提供前后端产物，只生成一套融合视图；不要分别生成“前端总览”和“后端总览”。
- 每个外部业务主机必须在至少一张图或表中可见。
- 每个顶层模块必须有一个主要导航链接。
- 每个模块文档链接必须指向已生成文件；如果链接目标不存在，说明模块文档生成不完整，应先补齐模块文档再完成总览。
- 只概括模块职责和协作关系，不复制模块文档细节。
- 使用后端 `analysis.json.summary` 说明后端规模（含 `total_remote_endpoints` 与 `total_remote_clients`，若存在）。
- 如果提供前端产物，同时使用前端 `analysis.json.summary` 说明页面数、组件数、API 定义数、页面流程数。
- 使用 `build_system.modules` 说明 Maven 结构，即使文档模块按业务域重新聚合。
- 使用 `dependencies.json.module_dependencies` 说明最终模块依赖方向，`external_module_dependencies` 说明对外部业务主机的依赖。
- 使用前端 `backend_api_usage.json` 说明页面触发的后端模块调用，不要凭后端 Controller 名称反推前端流程。
- 关键业务路径索引必须来自已生成模块文档、`matched_flows` 或重要后端入口；不得为了总览完整而新增没有证据支持的路径。
- Mermaid 图中的节点、边、外部业务主机必须能在 artifacts、源码或已生成模块文档中找到依据。
- 不输出交易码列表、完整接口参数表、完整 REST/RPC 清单或纯 API 目录。
- 不单独生成业务规则清单；只在待确认点中说明影响全局理解的证据限制。
- 避免宣传式或推测性描述。
- 如果使用了模型聚合，简要说明聚合依据来自 Maven、包名、Spring stereotype、入口和依赖图。
