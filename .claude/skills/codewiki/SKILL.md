---
name: codewiki
description: >
  基于 javawiki-analyzer 生成的 Java/Maven/Spring JSON 分析产物，为仓库生成分层代码 wiki。
  当用户要求为本地 Java 仓库、Git 仓库地址或已有 .nanobot-analysis / .tmp-*-analysis 产物生成或刷新架构文档、
  模块文档、接口文档、依赖说明、nanoclaw/nanobot 代码 wiki，或要求使用模型聚合模块边界时使用。
---

# CodeWiki Java 文档生成

这个 skill 是 CodeWiki 的 agent 侧流程，负责两件事：

- **模块聚合**：读取 `javawiki-analyzer` 的确定性分析事实和候选模块，必要时使用模型聚合出最终模块树。
- **文档生成**：只读取最终 artifacts，按模块顺序生成 Markdown 文档。

`javawiki-analyzer` 不内置模型调用。它只负责 Java 语法分析、Maven 扫描、组件索引、依赖图、入口识别和候选模块输出。模型聚合由当前 agent 完成，但聚合结果必须写回 JSON artifacts，不能在写文档时临时改变模块边界。

## 输入

开始前确认：

- 源码输入：Java/Maven 仓库路径、Git URL，或已有分析产物目录。
- 分析产物目录：默认 `.nanobot-analysis/`，也可以使用用户指定的 `.tmp-*-analysis/`。
- 文档输出目录：默认 `docs/`，除非用户指定其他目录。
- 是否需要模型聚合：如果用户关心业务域模块、CodeWiki 风格模块聚合，默认需要；如果只要求快速生成，可使用 analyzer 的规则 fallback。
- **前端分析产物**（可选）：若用户同时提供 Vue2 前端代码或 `vuewiki-analyzer` 产生的 `.frontend-analysis/`（含 `page_flows.json`、`backend_api_usage.json`、`api_index.json`），文档将以前端业务流程为主线编排。前端产物缺失时按纯后端流程生成，不阻塞主流程。
- 前端网关前缀（可选）：若前端通过网关（如 `/api/v1`、`/gateway`）转发到后端，提示用户在 prompt 中显式声明 `frontend_base_path`，对齐时会先剥离该前缀（见 [前后端对齐规则](references/frontend-backend-alignment.md) §2.2）。

如果后端分析产物不存在，先运行后端 analyzer：

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

## 分析产物

需要字段细节时读取 [分析产物 Schema](references/analysis-artifact-schema.md)。

核心文件：

- `analysis.json`
- `candidate_modules.json`
- `module_tree.json`
- `processing_order.json`
- `component_index.json`
- `dependencies.json`
- `modules/*.json`
- `components/*.json`

`candidate_modules.json` 是模型聚合输入；`module_tree.json`、`processing_order.json` 和 `modules/*.json` 是文档生成输入。若执行了模型聚合，必须先更新这些最终文档输入，再开始写 Markdown。

如果用户同时提供 Vue2 前端分析产物，读取 [前端分析产物 Schema](references/frontend-artifact-schema.md) 与 [前后端对齐规则](references/frontend-backend-alignment.md)。前端产物用于补充页面入口、用户触发点、API 调用顺序和后端接口使用清单；与后端融合时以规范化后的 `(HTTP_method, path)` 作为 join key，按 4 个匹配等级（exact / placeholder_normalized / suffix_match / unmatched）分类。

## 工作流

### 1. 检查分析状态

先读取 `analysis.json`。

检查：

- `schema_version` 是否支持。当前 skill 期望 `1.1`；遇到 `1.0` 仍可继续，但必须在最终报告中标注"远程调用信息可能不完整，建议重新运行 analyzer 以获取 `remote_endpoints` / `external_systems`"。低于 `1.0` 直接停止。
- `summary.language` 是否为 `java`。
- `build_system.type` 是否为 `maven` 或兼容的未知类型。
- `summary` 中 Java 文件数、组件数、REST endpoint 数是否符合预期。
- `diagnostics` 是否为空，或是否存在会影响文档可信度的问题。
- `aggregation.mode` 是否为 `rule_fallback`；如果是，说明当前模块树还没有经过模型聚合。
- 是否存在 `submodule_missing`、`submodule_update_failed`、`submodule_init_required`、`maven_module_path_missing` 或 `java_source_not_found` 诊断；如果存在，说明分析结果可能缺少源码或 submodule 中的代码。
- 如果存在 `java_source_outside_maven_modules`，说明 analyzer 找到了 Maven module 未覆盖的 Java 文件，通常来自 submodule、父 POM 自身源码或非标准目录；生成文档时应把这些临时 source module 当作分析证据，而不是 Maven 真实模块。
- 如果存在 `no_root_pom_discovered_subprojects`，说明仓库根没有 `pom.xml`，analyzer 已自动在子目录中发现 N 个独立 Maven 项目作为根（典型场景：容器仓库 + 多 Git submodule）。读取 `build_system.discovered_subproject_roots` 拿到自动识别出的子项目根列表；文档生成时应把每个子项目根视为独立的业务子系统（而非同一应用的不同分层模块），并在总览"分析说明"中明示该仓库是容器仓库结构。若同时存在 `submodule_missing`，说明部分子项目源码尚未拉取，文档中相关子项目应标注"源码缺失，本次未分析"。
- 如果存在 `no_pom_found`，说明整个仓库及子目录都找不到 `pom.xml`，analyzer 只能把整个仓库当作单一 Java 源码根处理；此时模块划分基于目录结构，不代表 Maven 真实组织，必须在文档中提示用户。

如果存在解析失败、跳过核心文件或依赖图异常，先向用户说明影响。
如果存在 submodule 或 Maven module 缺失诊断，正式文档生成前应建议用户重新拉取/初始化 submodule 后重跑 analyzer；用户仍要求继续时，必须在总览的"分析说明"中标注缺失范围。

如果指定了前端分析产物目录，额外检查：

- `page_flows.json` 与 `backend_api_usage.json` 是否存在；任一缺失则降级为纯后端流程，并在最终报告中说明。
- `analysis.json.framework.name` 是否为 `vue2` 或 `owl-vue2`（其他框架按 schema 允许范围处理，但需提示用户）。
- `summary.total_page_flows`、`summary.total_backend_calls` 是否大于 0；若全为 0 说明前端 analyzer 未识别出业务流，降级为纯后端流程。
- `diagnostics` 中是否存在 `frontend_runtime_path_unresolved`、`api_call_confidence_low_majority` 等会显著影响对齐质量的诊断；存在时需在总览"前后端对齐总览"小节明示。

### 2. 决定是否执行模型聚合

以下情况应执行模型聚合：

- 用户希望接近原 CodeWiki 的模块聚合效果。
- analyzer 的 `module_tree.json` 明显按技术层聚合，而不是按业务域或系统能力聚合。
- `analysis.json.aggregation.mode` 是 `rule_fallback`，且用户要生成正式 wiki。
- `candidate_modules.json` 存在且包含 Maven、包上下文、技术层、入口分组等候选边界。

以下情况可以跳过模型聚合：

- 用户明确要求使用现有 artifacts。
- 只是 smoke test 或快速验证文档生成链路。
- `module_tree.json` 已经是人工确认过的最终模块树。

### 3. 模型聚合阶段

执行聚合时，只读取必要的紧凑信息：

- `analysis.json`：仓库、Maven 模块、统计和诊断。
- `candidate_modules.json`：候选模块、候选来源、候选组件和候选依赖摘要。
- `component_index.json`：组件名称、包、stereotype、文件路径和 artifact 路径。
- `dependencies.json`：组件级和模块级依赖。

聚合目标：

- 生成业务上可解释的模块树，而不是简单按技术层拆分。
- 优先保留 Maven 多模块、业务包名、DDD 分层、REST 入口和依赖方向共同支持的边界。
- 为每个最终模块分配稳定 `module_id`、`name`、`kind`、`doc_path`、`component_ids`、`child_module_ids` 和 `depends_on_module_ids`。
- 子模块必须早于父模块出现在 `processing_order.json`。
- 每个组件只能归属一个最终叶子模块。

聚合完成后写回：

- `module_tree.json`
- `processing_order.json`
- `modules/*.json`
- `analysis.json.aggregation`

`analysis.json.aggregation.mode` 应改为 `agent_llm`，并记录 `candidate_artifact`、聚合时间、聚合说明和无法确定的边界。不要修改 `components/*.json`、`component_index.json` 或源码仓库。

### 4. 验证最终生成计划

写文档前验证：

- 每个必需 artifact 存在。
- `module_tree.modules` 中每个 `module_id` 唯一。
- 每个模块 `doc_path` 唯一，且位于文档输出目录内。
- `processing_order.steps` 覆盖所有模块，且子模块早于父模块。
- 每个 `modules/<module_id>.json` 存在，并与 `module_tree.json` 一致。
- 每个 `component_index` 中的 `artifact_path` 指向存在的 `components/*.json`。
- 每个组件最多出现在一个最终叶子模块中。

计划无效时停止生成，列出缺少或冲突的文件和字段。

### 4.5 入口流程清单生成

写文档前，必须先扫描所有叶子模块产出"入口流程清单"，作为后续写作的依据。该清单不写入磁盘，仅作为本次生成的工作内存。

#### 4.5.A 后端入口与调用链还原（始终执行）

1. 遍历每个叶子模块 `modules/<module_id>.json` 的 `entry_points`，加上每个组件 `components/*.json` 中的 `entry_points`（REST endpoint 与 outbound endpoint 都纳入）；以及 `@Scheduled` / `@RabbitListener` / `@KafkaListener` / `@EventListener` 注解的方法（来自 `components/*.json.methods[].annotations`）。
2. 对每个入口，沿 `methods[].calls[].resolved_component` 递归还原调用链。递归限深 6，去环（同一 `component_id` 在一条链上不重复展开）。
3. 同时记录命中的 `remote_endpoints`（来自每个被访问组件的顶层 `remote_endpoints` 或方法内嵌 `remote_endpoints`）。
4. 对每个入口判定是否为 **重要入口**，命中任一即标记：
   - 调用深度 ≥ 3；
   - 调用链命中至少一个 `remote_endpoints` 项（不论 analyzer 提取的 URL 是否完整）；
   - 调用链跨越 ≥ 2 个不同 stereotype；
   - 入口本身是 `@Scheduled` / 消息消费者 / 事件监听。
5. 阈值可由用户在 prompt 中覆盖（如 "把所有 endpoint 都当作重要入口"）。
6. 若 `schema_version < 1.1` 或 `summary.total_remote_endpoints` 字段缺失，按 [远程调用 fallback 识别](references/remote-call-recognition.md) 在写文档时补充识别。

#### 4.5.B 前后端对齐（仅当存在前端分析产物时执行）

详细规则见 [前后端对齐规则](references/frontend-backend-alignment.md)。在 4.5.A 完成后追加以下步骤：

**Step 1：构建后端 endpoint 索引**

把所有后端 REST `entry_points` 的 `(http_method, path)` 按 [对齐规则](references/frontend-backend-alignment.md) §2 规范化（占位符归一、统一斜杠）后建索引：`(METHOD, normalized_path) → entry_point`。同一规范化 key 命中多个 entry_point 时全部保留（多义需后续提示）。

**Step 2：剥离网关前缀（若需要）**

- 用户显式声明 `frontend_base_path` → 在 Step 3 规范化前从前端 path 中剥离。
- 未声明但 `api_index.json` 中所有 path 都以同一前缀开头且该前缀不在后端索引中 → 自动推断并提示用户确认。

**Step 3：遍历前端 flow 并连接**

读取 `page_flows.json.flows[]`，对每条 flow 按 `order` 升序遍历 `steps[]`：

- 对每个 `type == api_call` 的 step，规范化 `(method, path)` 后查后端索引。
- 按 [对齐规则](references/frontend-backend-alignment.md) §3 优先级匹配：exact → placeholder_normalized → suffix_match → unmatched。每命中即停止后续等级尝试。
- 把 step 的 `(method, path, function_name, defined_in, confidence)` 与命中的 `entry_point` / `match_level` 记入工作内存。

**Step 4：分类**

按 [对齐规则](references/frontend-backend-alignment.md) §4 输出三个集合：

- `matched_flows`：所有 step 都命中（或仅个别 step unmatched）的 flow；每条 flow 含 `overall_confidence`（按 §4.1 公式计算）。
- `frontend_only_flows`：所有 step 均 unmatched 的 flow，或主要业务 step 未匹配的 flow，原样保留，标 `reason: unmatched`。
- `backend_only_endpoints`：未被任何前端 flow 引用的后端 entry_point，复用 4.5.A 的"重要入口"启发式标 `importance: important | minor`。

**Step 5：把 matched_flow 归属到后端叶子模块**

对每条 `matched_flow.steps[i].backend_entry_point`，根据 entry_point 所在 component_id 反查 `module_tree.json` 找到其所属叶子模块；同一条 flow 的多个 step 可跨多个模块，每个被涉及到的后端叶子模块的工作内存中追加 `frontend_driven_flows: [...]`。

#### 4.5.C 工作内存形态

- 每个叶子模块的工作内存：`{ 重要入口列表, 次要入口列表, 命中的外部系统集合, frontend_driven_flows (可选), frontend_only_flows (可选, 仅本模块涉及) }`
- 重要入口含：`entry_qualified_name#method`、`file_path:Lstart-Lend`、调用链节点序列（含每步行号）、命中的 remote_endpoints 列表。
- `frontend_driven_flows` 含：`flow_id`、`page_title`、`biz_id`、`trigger`、`steps[]`（含 `match_level`、`backend_entry_point`、`confidence`）、`overall_confidence`。
- overview 工作内存额外保存 `matched_flows / frontend_only_flows / backend_only_endpoints` 全集，用于生成 §5「关键流程」与 §5.X「前后端对齐总览」。

### 5. 生成模块文档

#### 5.1 选择文档结构

读完 §4.5 清单后，统计叶子模块数：

- **叶子模块数 ≤ 8**：使用扁平结构。直接在文档输出根目录写 `<module_name>.md`（如 `docs/order.md`），不进入 `modules/` 子目录；忽略原 `module_tree.json` 中的父/叶嵌套，所有模块平铺。`overview.md` 链接直接指向这些文件。
- **叶子模块数 > 8**：保留 `module_tree.json` 中的父/叶嵌套，按原 `doc_path` 写入；父模块文档负责导航到子模块。

不论何种结构，每个模块的 `doc_path` 必须唯一，且最终写入位置必须在文档输出目录内。如果实际写入路径与 `module_tree.json` 中记录的 `doc_path` 不一致（扁平化重写），在最终报告中说明。

#### 5.2 处理顺序

严格按照 `processing_order.json.steps` 处理模块。叶子模块在前，父模块在后（即使采用扁平结构，仍按此顺序保证内部一致性）。

#### 5.3 叶子模块写作流程

1. 读取 `modules/<module_id>.json`。
2. 按需读取组件详情 JSON，优先读取 Controller、Service、Configuration、领域模型、远程客户端、消息处理器、任务处理器和跨模块依赖来源；DTO/VO/枚举仅在决定流程时读取。
3. 按 [模块文档模板](references/module-document-template.md) §"叶子模块" 的 10 段固定章节顺序输出。**章节顺序不得调整，不得跳过**；无内容的章节写"无（说明原因）"。
4. **第 4 节"数据流"**：
   - **若工作内存中本模块含 `frontend_driven_flows`（即存在前端 analysis）**：先按 [模块文档模板 §4.0](references/module-document-template.md) 写「前端业务流程」，每条 `matched_flow` 按 [流程章节模板 §2.1](references/flow-section-template.md) 「前端驱动变体」展开（含 User/Page/FrontApi 起手的 sequenceDiagram、步骤详解、`match_level` 与 `confidence` 措辞、alt 分支）。然后在 §4.X「后端入口」中处理剩余的**重要入口**——已在 §4.0 出现过的 endpoint 不重复展开，正文写"见 §4.0.X"交叉引用即可；**次要入口**按 [流程章节模板 §3](references/flow-section-template.md) 简写。若本模块涉及 `frontend_only_flows`，在 §4.0 末尾追加"前端独立流程"小节，明示"未在当前后端代码中找到对应 endpoint"。
   - **无前端 analysis 时**：跳过 §4.0，对本模块所有**重要入口**严格按 [流程章节模板 §2](references/flow-section-template.md) 展开（含 sequenceDiagram + 编号步骤 + 行号锚点 + alt 异常分支）；**次要入口**按 §3 简写。
5. **第 5 节"集成点"**：使用 `modules/*.json.remote_endpoints` 直接填表；若该字段为空但 fallback 识别到远程调用，按 [远程调用识别](references/remote-call-recognition.md) 填表并标"来源 = fallback"。
6. 每个 Spring 组件标题下**必须**紧跟 `**File**: <file_path>`。
7. 所有源码引用必须含 `<file_path>:Lstart-Lend`；找不到行号时显式标注"行号未知"。前端步骤的行号若 `page_flows.json` 未提供，写"(行号未知，前端 analyzer 未提供 span)"。
8. 写入模块的最终 `doc_path`（按 §5.1 决定的结构）。
9. 检查 Markdown 链接和 Mermaid 图。

#### 5.4 父模块写作流程（仅在分层结构下使用）

1. 读取 `modules/<module_id>.json` + 子模块的 `entry_points` 与 `external_systems` 汇总。
2. 按模板"父模块"6 段结构输出。
3. **第 4 节"跨模块业务流"**：必须至少一张跨越多个子模块的 sequenceDiagram，按 [流程章节模板](references/flow-section-template.md) §2 展开（含行号锚点 + alt 分支）。不要写"API → Application → Domain → Persistence"这种空泛分层套话。
4. 不复制子模块组件细节。

#### 5.5 硬约束

以下任一项违反必须修复后才能进入下一步：

- 每个重要入口对应的"数据流"段缺少 sequenceDiagram、行号锚点或 alt 分支。
- 远程调用未在"集成点"列出 URL（或显式 `${...}` / `<unresolved>` 标记）。
- 任何 Spring 组件标题下缺 `**File**`。
- 任何流程步骤引用的类名、方法名、字段名在 `components/*.json` 中找不到（必须删除该引用，不得杜撰）。
- 出现 "通过 RestTemplate 调用外部服务" 这类无方法、无 URL、无行号的空表述。

### 6. 生成仓库总览

所有模块文档完成后生成 `overview.md`。

总览使用：

- `analysis.json` 的仓库信息、Maven 模块、统计和诊断。
- `module_tree.json` 的顶层模块导航。
- `dependencies.json` 的依赖方向。
- 已完成模块文档的简短摘要。
- 若存在前端 analysis：§4.5.B 工作内存中的 `matched_flows / frontend_only_flows / backend_only_endpoints` 全集。

总览结构参考 [仓库总览模板](references/overview-template.md)。

若存在前端 analysis：

- §5「关键流程」必须优先从 `matched_flows.overall_confidence == high` 中挑选 2-4 条端到端业务路径，参与者覆盖 `User → Page.vue → frontend API → Controller → Service → RemoteClient → External`。
- 必须输出 §5.X「前后端对齐总览」小节（对齐统计、对齐方式分布、典型未对齐流程示例）。
- 若大多数 `matched_flows` 是 `suffix_match`，在总览中提示用户确认网关前缀。

### 7. 完成前校验

报告前检查：

- `overview.md` 存在。
- 每个模块 `doc_path` 存在。
- 内部 Markdown 链接可解析。
- Mermaid 语法已用可用工具校验；如果没有校验工具，明确说明。
- 文档没有引用 artifacts 或源码片段中不存在的类、接口、依赖或流程。
- 没有修改源码仓库。
- 除模型聚合需要写回的最终 artifacts 外，没有无关修改分析产物。

最终报告生成文件、分析统计、聚合模式、诊断信息和仍不确定的边界。

## 失败处理

- analyzer 不可用且没有现成 artifacts：停止并说明需要先运行 `javawiki analyze`。
- 远端仓库或本地仓库存在 submodule 缺失：优先建议使用 `--submodules auto` 或 `--init-submodules` 重新分析；继续生成时必须记录限制。
- `candidate_modules.json` 缺失但需要模型聚合：使用现有 `module_tree.json` 前先说明只能使用规则 fallback。
- artifacts schema 不匹配：停止生成，列出不兼容文件或字段。
- 模型聚合无法确定业务边界：保留更高层模块，记录原因，不强行细拆。
- 组件源码过大：只读取当前模块所需的方法、注解、字段和依赖记录。
- 组件源码或解析数据缺失：在文档中标注限制，避免推测性说明。
- `remote_url_unresolved` 数量较多（占 `total_remote_endpoints` ≥ 30%）：在每个受影响模块文档"维护注意事项"中明示，并提示用户检查 `application.yml` 中的占位符配置或重跑 analyzer。
- 前端 analysis 缺失或解析失败（`page_flows.json` 不存在 / `summary.total_page_flows == 0` / 关键 diagnostic）：自动降级为纯后端流程，不再生成 §4.0「前端业务流程」与 §5.X「前后端对齐总览」，并在最终报告中说明降级原因。
- 前端 endpoint 与后端 0 命中（`matched_flows` 为空但 `frontend_only_flows` 非空）：保留 frontend_only_flows 章节并在总览警示"当前后端代码与前端 API 集合不在同一服务边界，疑似只是前端 + 网关 / 中台调用"。
- 前端 `confidence == low` 的 step 比例超过 50%：在总览"前后端对齐总览"小节明示对齐结果可信度受限，建议用户查看前端 analyzer 的 `frontend_runtime_path_unresolved` 诊断。
