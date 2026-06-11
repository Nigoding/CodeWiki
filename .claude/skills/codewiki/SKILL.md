---
name: codewiki
description: >
  基于 javawiki-analyzer 和 vuewiki-analyzer 生成的 JSON 分析产物，为 Java/Maven/Spring 后端仓库、
  Vue2/Owl 前端仓库或前后端组合仓库生成面向开发人员的业务代码 wiki。用户同时提供前端仓库和后端仓库时，
  必须生成一份融合前端页面流程与后端调用链的业务文档，而不是分别为两个仓库生成独立文档。用户要求生成或刷新架构文档、
  模块文档、页面业务流程、接口调用链、前后端接口对齐、依赖说明、nanoclaw/nanobot 代码 wiki，
  或要求使用模型聚合业务模块边界时使用。
---

# CodeWiki 业务文档生成

这个 skill 是 CodeWiki 的 agent 侧流程，负责三件事：

- **模块聚合**：读取 `javawiki-analyzer` 的确定性分析事实和候选模块，必要时使用模型聚合最终业务模块树。
- **前后端流程融合**：读取 `vuewiki-analyzer` 的页面、组件和 API 调用流程，把页面触发顺序映射到后端接口。
- **文档生成**：只基于最终 artifacts 生成业务 wiki，不凭空补充 artifacts 或源码中不存在的信息。

`javawiki-analyzer` 和 `vuewiki-analyzer` 都不内置模型调用。代码分析、组件索引、依赖抽取、入口识别由 analyzer 完成；业务模块聚合、前后端对齐解释和文档写作由当前 agent 完成。若执行模型聚合，必须先把结果写回最终 JSON artifacts，再开始生成 Markdown。

**联合生成原则**：当用户同时提供后端仓库和前端仓库，或同时提供 `.nanobot-analysis/` 与 `.frontend-analysis/` 时，默认意图是生成一份业务系统文档。后端是模块、接口、调用链和集成点的主干；前端是页面入口、用户触发顺序和业务流程主线。不要把两个仓库拆成两个独立文档任务，除非用户明确要求“分别生成”。

**业务文档边界**：CodeWiki 生成的是业务说明文档，不是 API 文档、交易码清单或代码细节索引。默认聚焦业务场景、业务流程、模块协作、外部业务主机依赖和源码可证实的依赖关系；不要输出交易码列表、完整 API 表格、参数清单或接口手册式内容。接口路径和方法只能作为业务流程证据出现。

**整合目标**：同时存在前端和后端产物时，默认生成一份完整业务说明文档，同时包含前端调用入口、后端接口承接和后端调用链。只有用户明确要求“只关注后端业务流程”时，才可简化前端对齐内容。

**证据优先原则**：业务流程、场景分支、模块依赖和 Mermaid 图都必须有 artifacts 或源码证据。没有代码证据时，只能写“当前源码未确认”或“需结合下游文档确认”，不得按业务常识补全流程、分支、字段、状态或外部系统。证据清单用于生成约束，正文默认不输出源码行号、源码片段或完整证据链。

## 输入

开始前确认：

- 后端输入：Java/Maven 仓库路径、Git URL，或已有后端分析产物目录。
- 前端输入（可选）：Vue2/Owl 仓库路径、Git URL，或已有前端分析产物目录。
- 后端分析产物目录：默认 `.nanobot-analysis/`，也可使用用户指定目录。
- 前端分析产物目录：默认 `.frontend-analysis/`，也可使用用户指定目录。
- 文档输出目录：默认 `docs/`，除非用户指定其他目录。
- 联合输出：存在前端输入时仍只生成一个文档输出目录；`overview.md`、模块文档和关键业务路径索引都应融合前后端信息。
- 前端网关前缀（可选）：若前端经 `/api/v1`、`/gateway` 等网关转发，提示用户声明 `frontend_base_path`，对齐规则见 [前后端对齐规则](references/frontend-backend-alignment.md)。
- 模块聚合策略：正式 wiki 默认需要模型聚合；快速验证、用户明确要求使用现有 artifacts、或 `module_tree.json` 已人工确认时可跳过。

## 运行分析器

如果后端分析产物不存在，先运行：

```bash
javawiki analyze <repo-path-or-git-url> -o <analysis-dir> --submodules auto
```

如果 `javawiki` 命令不可用，但当前工作区存在 `javawiki-analyzer` 源码：

```bash
PYTHONPATH=javawiki-analyzer python -m javawiki_analyzer.cli.main analyze <repo-path-or-git-url> -o <analysis-dir> --submodules auto
```

Windows PowerShell 使用：

```powershell
$env:PYTHONPATH='javawiki-analyzer'; python -m javawiki_analyzer.cli.main analyze <repo-path-or-git-url> -o <analysis-dir> --submodules auto
```

如果输入是本地仓库且存在 Git submodule，默认只检测状态，不修改用户工作区。用户明确要求初始化本地 submodule 时，再运行：

```bash
javawiki analyze <local-repo-path> -o <analysis-dir> --init-submodules
```

如果提供了前端仓库且前端分析产物不存在，运行：

```bash
vuewiki analyze <frontend-repo-path-or-git-url> -o <frontend-analysis-dir> --submodules auto
```

如果 `vuewiki` 命令不可用，但当前工作区存在 `vuewiki-analyzer` 源码：

```bash
node vuewiki-analyzer/src/cli.js analyze <frontend-repo-path-or-git-url> -o <frontend-analysis-dir> --submodules auto
```

前后端 analyzer 可以分别输出分析产物目录，但 Markdown 文档输出必须合并到同一个 `docs/` 中。前端产物用于补充模块文档中的场景流程，并用于 `overview.md` 的页面到模块映射图和关键业务路径索引，不是单独生成一套前端 wiki。

## 产物与引用

后端字段细节见 [后端分析产物 Schema](references/analysis-artifact-schema.md)。核心输入：

- `analysis.json`
- `candidate_modules.json`
- `module_tree.json`
- `processing_order.json`
- `component_index.json`
- `dependencies.json`
- `modules/*.json`
- `components/*.json`

前端字段细节见 [前端分析产物 Schema](references/frontend-artifact-schema.md)。核心输入：

- `analysis.json`
- `module_tree.json`
- `page_index.json`
- `component_index.json`
- `api_index.json`
- `page_flows.json`
- `backend_api_usage.json`
- `modules/`
- `pages/`
- `components/`

按需读取这些参考文件，不要把所有规则一次性载入：

- 模块文档结构：[模块文档模板](references/module-document-template.md)
- 功能全景视图结构：[功能全景视图模板](references/overview-template.md)
- 场景流程章节：[流程章节模板](references/flow-section-template.md)
- 前后端接口匹配：[前后端对齐规则](references/frontend-backend-alignment.md)
- 源码依赖分析：[依赖分析规则](references/dependency-analysis-rules.md)
- 远程调用兜底识别：[远程调用识别规则](references/remote-call-recognition.md)
- Mermaid 语法约束：[Mermaid 规则](references/mermaid-rules.md)

## 工作流

### 1. 检查分析状态

先读取后端 `analysis.json`，必要时读取前端 `analysis.json`。schema 或关键文件不兼容时停止生成并说明缺失字段。

后端检查：

- `schema_version` 支持：当前期望 `1.1`；遇到 `1.0` 可继续，但最终报告必须提示远程调用信息可能不完整；低于 `1.0` 停止。
- `summary.language == "java"`。
- `build_system.type` 为 `maven` 或 analyzer 支持的未知兼容类型。
- `summary` 中 Java 文件数、组件数、REST endpoint 数符合预期。
- `diagnostics` 中是否存在会影响可信度的问题，特别是 `submodule_missing`、`submodule_update_failed`、`submodule_init_required`、`maven_module_path_missing`、`java_source_not_found`、`java_source_outside_maven_modules`、`no_root_pom_discovered_subprojects`、`no_pom_found`。
- `aggregation.mode == "rule_fallback"` 时，说明模块树尚未经过模型聚合。

存在 submodule 或 Maven module 缺失诊断时，正式生成前建议用户重新拉取或初始化 submodule 后重跑 analyzer；用户仍要求继续时，在总览“分析说明”中标注缺失范围。

前端检查：

- 必需文件 `page_flows.json`、`backend_api_usage.json`、`api_index.json` 存在；任一缺失则降级为纯后端流程。
- `framework.name == "vue"` 且 `framework.version_major == 2`。
- `entry_config.path` 为 `owl.config.js` 或用户确认的入口配置。
- `summary.total_pages`、`summary.total_api_defs`、`summary.total_page_flows`、`summary.total_backend_api_usages` 符合预期；若 `total_page_flows == 0` 或 `total_backend_api_usages == 0`，降级为纯后端流程。
- `diagnostics` 中若存在 `owl_config_not_found`、`owl_pages_not_found`、`page_entry_not_found`、`submodule_missing`、`frontend_runtime_path_unresolved`、`api_call_confidence_low_majority`，在总览中说明对齐可信度限制。

### 2. 决定是否模型聚合

以下情况执行模型聚合：

- 用户希望接近原 CodeWiki 的业务模块聚合效果。
- `module_tree.json` 明显按技术层聚合，而不是按业务域或系统能力聚合。
- `analysis.json.aggregation.mode == "rule_fallback"`，且用户要生成正式 wiki。
- `candidate_modules.json` 存在，且包含 Maven、包上下文、技术层、入口分组等候选边界。

聚合只读取紧凑信息：`analysis.json`、`candidate_modules.json`、`component_index.json`、`dependencies.json`。聚合目标是生成业务上可解释的模块树；每个组件只能归属一个最终叶子模块；子模块必须早于父模块出现在 `processing_order.json`。

聚合完成后只写回：

- `module_tree.json`
- `processing_order.json`
- `modules/*.json`
- `analysis.json.aggregation`

`analysis.json.aggregation.mode` 应改为 `agent_llm`，并记录候选输入、聚合时间、聚合说明和不确定边界。不要修改 `components/*.json`、`component_index.json` 或源码仓库。

### 3. 验证最终生成计划

写 Markdown 前验证：

- 必需 artifacts 存在。
- `module_tree.modules[].module_id` 唯一。
- 每个模块 `doc_path` 唯一，且最终写入位置位于文档输出目录内。
- `processing_order.steps` 覆盖所有模块，且子模块早于父模块。
- 每个 `modules/<module_id>.json` 存在，并与 `module_tree.json` 一致。
- 每个 `component_index` 的 `artifact_path` 指向存在的 `components/*.json`。
- 每个组件最多出现在一个最终叶子模块中。

如果存在前端产物，额外验证：

- `page_index.json`、`component_index.json`、`api_index.json`、`page_flows.json`、`backend_api_usage.json` 存在。
- `page_id`、`flow_id` 唯一。
- `backend_api_usage.json` 中的 `method + path` 可用于匹配后端 `entry_points`；匹配不到的调用进入 `frontend_only_flows`，不得丢弃。

### 3.5 证据约束与幻觉防护

写 Markdown 前先建立“证据清单”。每条业务流程、场景分支、模块依赖和图中的边都必须能回到以下至少一种证据：

- 后端 `components/*.json` 中的 `entry_points`、`methods[].calls[]`、`remote_endpoints`、`fields[]` 或 `source_code`。
- 后端 `dependencies.json`、`modules/*.json`、`analysis.json.build_system`。
- 前端 `page_flows.json`、`api_index.json`、`backend_api_usage.json`、`pages/*.json` 或 `components/*.json`。
- 必要时用源码检索补充确认的 `import`、注入字段、方法调用、配置项或异常抛出位置。

硬约束：

- 没有入口证据时，不生成业务流程。
- 没有调用链证据时，只能写入口职责，不能扩展成端到端流程。
- 没有异常、状态流转、条件分支或配置证据时，不生成对应业务分支或判断。
- Mermaid 图中的每个节点和每条边都必须来自证据清单；不能为了图完整而补节点或补边。
- 使用推断性语言时必须标注证据不足，例如“当前源码未确认该行为”；不得写成确定事实。
- 若证据不足影响理解，在模块文档“待确认点”或总览“分析说明”中说明限制。
- 正文默认只保留轻量实现引用，如类名、方法名、组件名；不要输出源码行号、源码片段或逐步证据表，除非用户明确要求代码级追踪。

### 4. 入口流程清单生成

写文档前必须先生成“入口流程清单”，仅作为本次生成的工作内存，不写入磁盘。

后端入口始终执行：

- 遍历叶子模块和组件中的 REST `entry_points`，并纳入 `@Scheduled`、`@RabbitListener`、`@KafkaListener`、`@EventListener` 方法。
- 沿 `methods[].calls[].resolved_component` 递归还原调用链，默认限深 6，并对同一调用链去环。
- 记录调用链命中的 `remote_endpoints`。
- 命中任一条件即标记为重要入口：调用深度 ≥ 3、命中远程调用、跨 ≥ 2 个 stereotype、入口本身是定时任务/消息消费者/事件监听。
- 若 `schema_version < 1.1` 或远程调用字段缺失，按 [远程调用识别规则](references/remote-call-recognition.md) 补充识别。

存在前端产物时，按 [前后端对齐规则](references/frontend-backend-alignment.md) 生成工作内存：

- `matched_flows`：前端 flow 中的 API 调用能对齐到后端 endpoint。
- `frontend_only_flows`：前端 flow 未在当前后端代码中找到对应 endpoint。
- `backend_only_endpoints`：未被任何前端 flow 引用的后端入口。

每个叶子模块的工作内存至少包含：

- 重要入口列表。
- 次要入口列表。
- 命中的外部系统集合。
- `frontend_driven_flows`（存在前端 analysis 时可选）。
- `frontend_only_flows`（存在前端 analysis 且与本模块相关时可选）。

overview 工作内存额外保存 `matched_flows / frontend_only_flows / backend_only_endpoints` 全集，用于总览中的页面到模块映射图、关键业务路径索引和分析说明。

### 5. 生成模块文档

**禁止 overview-only 输出**：正式文档生成必须先生成模块文档，再生成 `overview.md`。不得只生成一份总览文档来替代子模块文档；不得把“后续可继续补充模块文档”作为完成状态。

先根据叶子模块数量决定文档结构：

- 叶子模块数 ≤ 8：使用扁平结构，模块文档直接写到输出根目录。
- 叶子模块数 > 8：保留 `module_tree.json` 中的父/叶结构，按 `doc_path` 写入。

严格按照 `processing_order.json.steps` 处理模块，叶子模块先于父模块。

每个 `processing_order.json.steps[].module_id` 都必须产生对应的模块文档。模块数量过多时可以分批生成，但不能跳过模块、合并模块，或只输出 `overview.md`。

叶子模块写作：

- 读取 `modules/<module_id>.json`，按需读取组件详情 JSON。
- 按 [模块文档模板](references/module-document-template.md) 的叶子模块 8 段固定章节输出，不得调整顺序。
- 篇幅优先给模块定位、业务上下文、核心业务场景和场景流程；组件清单、源码行号和代码细节默认不输出。
- 第 4 节“场景流程详解”按 [流程章节模板](references/flow-section-template.md) 展开；存在前端对齐结果时，优先写 `frontend_driven_flows`，已在前端流程中出现过的后端 endpoint 不重复展开。
- 不单独生成“业务规则”章节；只有直接改变场景走向的判断、分支、拒绝、降级或兜底逻辑，才写入对应场景的“分支与异常路径”。
- 第 5 节“业务数据来源与外部依赖”合并关键数据、业务状态和外部系统交互，重点说明 RPC/HTTP/中台服务提供的数据、用途和失败影响。
- 依赖关系按 [依赖分析规则](references/dependency-analysis-rules.md) 生成；不要仅根据 `pom.xml` 推断模块依赖。
- 远程依赖使用 `modules/*.json.remote_endpoints`；字段不足时按 [远程调用识别规则](references/remote-call-recognition.md) fallback，并在“待确认点”说明来源限制。
- 相关实现只输出轻量引用：`XxxController#method`、`XxxService#method`、`XxxClient#method`、前端 `xxxApi()`。不要输出源码行号、源码片段或完整调用链证据。

父模块写作：

- 只在分层结构下生成。
- 读取父模块及其子模块摘要，按 [模块文档模板](references/module-document-template.md) 的父模块结构输出。
- 至少提供一条真实可追溯的跨模块业务流，不复制子模块组件细节。

### 6. 生成功能全景总览

所有模块文档完成后生成 `overview.md`，结构参考 [功能全景视图模板](references/overview-template.md)。如果任一 `processing_order.json.steps[].module_id` 对应的模块文档尚未生成，不得进入总览生成阶段。

总览是功能全景视图，必须使用：

- `analysis.json` 的仓库、构建系统、统计和诊断信息。
- `module_tree.json` 的顶层模块导航。
- `dependencies.json` 的依赖方向。
- 已完成模块文档的简短摘要。
- 若存在前端 analysis：`matched_flows / frontend_only_flows / backend_only_endpoints` 全集。

存在前端 analysis 时，总览必须包含“页面到模块映射图”和“关键业务路径索引”；未匹配、低置信度或动态路径等对齐限制统一放入“分析说明与待确认点”。关键业务路径优先选择已生成模块文档中的核心场景和 `matched_flows.overall_confidence == high` 的路径。

不要在同一次任务中生成“后端 overview”和“前端 overview”两份并列总览；联合场景只生成一份 `overview.md`，其功能上下文图同时包含前端页面、后端模块和外部业务主机。

### 7. 完成前校验

报告前检查：

- `overview.md` 存在。
- 每个模块 `doc_path` 存在。
- `processing_order.json.steps` 中每个 `module_id` 都已生成对应文档；不能只有 `overview.md`。
- `overview.md` 只做导航和全景总结，不能替代模块文档。
- 文档没有退化成 API 文档：不得存在交易码列表、完整接口参数表或大段接口清单。
- 每条业务流程、场景分支、模块依赖和 Mermaid 边都能追溯到证据清单；证据不足处已明确标注，未写成确定事实。
- 内部 Markdown 链接可解析。
- Mermaid 语法符合 [Mermaid 规则](references/mermaid-rules.md)；如果没有可用校验工具，最终报告中说明。
- 文档没有引用 artifacts 或源码中不存在的类、接口、依赖、字段、URL 或流程。
- 没有修改源码仓库。
- 除模型聚合需要写回的最终 artifacts 外，没有无关修改分析产物。

最终报告说明生成文件、分析统计、聚合模式、关键诊断和仍不确定的边界。

## 迭代确认

首轮完整生成、批量重写或重要结构调整后，向用户确认是否符合预期，说明：

- 已生成或修改的文档范围。
- 已覆盖的核心业务路径。
- 仍不确定或证据不足的边界。

在用户确认方向前，不要连续多轮大规模重写。用户明确指出问题后，下一轮只围绕该问题修正；重要修改前先确认方向。

## 失败处理

- analyzer 不可用且没有现成 artifacts：停止并说明需要先运行对应 analyzer。
- 远端仓库或本地仓库存在 submodule 缺失：优先建议用 `--submodules auto` 或 `--init-submodules` 重新分析；继续生成时必须记录限制。
- `candidate_modules.json` 缺失但需要模型聚合：只能使用现有 `module_tree.json`，并说明模块边界来自规则 fallback。
- artifacts schema 不匹配：停止生成，列出不兼容文件或字段。
- 模型聚合无法确定业务边界：保留更高层模块，记录原因，不强行细拆。
- 组件源码或解析数据缺失：在文档中标注限制，避免推测性说明。
- `remote_url_unresolved` 数量较多：在受影响模块的“待确认点”中提示配置项或远程 URL 解析不完整。
- 前端 analysis 缺失或解析失败：自动降级为纯后端视角，不生成页面到模块映射图，并在最终报告中说明原因。
- 前端 endpoint 与后端 0 命中：保留 `frontend_only_flows`，并提示当前前端 API 集合可能不属于该后端服务边界。
- 前端 `confidence == low` 的 step 比例过高：在“分析说明与待确认点”中说明可信度受限。
