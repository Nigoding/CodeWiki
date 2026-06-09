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

如果分析产物不存在，先运行 analyzer：

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

如果用户同时提供 Vue2 前端分析产物，读取 [前端分析产物 Schema](references/frontend-artifact-schema.md)。前端产物用于补充页面入口、用户触发点、API 调用顺序和后端接口使用清单；与后端融合时以 `backend_api_usage.json` 的 `method + path` 匹配后端 `entry_points`。

## 工作流

### 1. 检查分析状态

先读取 `analysis.json`。

检查：

- `schema_version` 是否支持。
- `summary.language` 是否为 `java`。
- `build_system.type` 是否为 `maven` 或兼容的未知类型。
- `summary` 中 Java 文件数、组件数、REST endpoint 数是否符合预期。
- `diagnostics` 是否为空，或是否存在会影响文档可信度的问题。
- `aggregation.mode` 是否为 `rule_fallback`；如果是，说明当前模块树还没有经过模型聚合。
- 是否存在 `submodule_missing`、`submodule_update_failed`、`submodule_init_required`、`maven_module_path_missing` 或 `java_source_not_found` 诊断；如果存在，说明分析结果可能缺少源码或 submodule 中的代码。
- 如果存在 `java_source_outside_maven_modules`，说明 analyzer 找到了 Maven module 未覆盖的 Java 文件，通常来自 submodule、父 POM 自身源码或非标准目录；生成文档时应把这些临时 source module 当作分析证据，而不是 Maven 真实模块。

如果存在解析失败、跳过核心文件或依赖图异常，先向用户说明影响。
如果存在 submodule 或 Maven module 缺失诊断，正式文档生成前应建议用户重新拉取/初始化 submodule 后重跑 analyzer；用户仍要求继续时，必须在总览的“分析说明”中标注缺失范围。

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

### 5. 生成模块文档

严格按照 `processing_order.json.steps` 处理模块。

每个模块：

1. 读取 `modules/<module_id>.json`。
2. 根据 `kind` 和 `child_module_ids` 判断叶子模块或父模块。
3. 叶子模块只按需读取组件详情 JSON，优先读取 Controller、Service、Repository、Configuration、领域模型、远程客户端、消息处理器、任务处理器和跨模块依赖来源。
4. 父模块只总结直接子模块和模块级依赖，链接子模块文档，不复制组件细节。
5. 写入模块自己的 `doc_path`。
6. 检查 Markdown 链接和 Mermaid 图。

文档结构参考 [模块文档模板](references/module-document-template.md)，图表规则参考 [Mermaid 规则](references/mermaid-rules.md)。

### 6. 生成仓库总览

所有模块文档完成后生成 `overview.md`。

总览使用：

- `analysis.json` 的仓库信息、Maven 模块、统计和诊断。
- `module_tree.json` 的顶层模块导航。
- `dependencies.json` 的依赖方向。
- 已完成模块文档的简短摘要。

总览结构参考 [仓库总览模板](references/overview-template.md)。

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
