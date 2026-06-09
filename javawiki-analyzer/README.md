# JavaWiki Analyzer

JavaWiki Analyzer 是一个面向 Java/Maven 仓库的静态代码分析工具。它不生成 Markdown 文档，也不内置模型调用，只输出给 nanobot、nanoclaw 或其他文档生成 agent 使用的 JSON artifacts。

它的定位是：

- 解析 Java 代码事实。
- 扫描 Maven 单模块或多模块结构。
- 提取 Spring stereotype、REST endpoint、组件依赖和候选模块边界。
- 输出 `candidate_modules.json`，供 agent/skill 使用模型做 CodeWiki 风格模块聚合。
- 输出规则兜底的 `module_tree.json`，用于没有模型聚合时的快速文档生成。

## MVP 范围

已支持：

- 本地仓库路径和远端 Git 仓库 URL。
- 远端仓库自动递归拉取 Git submodule。
- 本地仓库 submodule 检测；显式传参后可初始化本地 submodule。
- Maven 单模块和多模块项目。
- `src/main/java/**/*.java` 扫描。
- 基于 tree-sitter 的 Java class/interface/enum/record 提取。
- Spring stereotype 和 REST endpoint 提取。
- 尽力而为的类级依赖分析。
- 面向 agent 侧模型聚合的确定性候选模块边界。
- Nanobot JSON artifacts。

暂不支持：

- Gradle。
- 增量分析。
- MCP。
- 内置 LLM 调用。
- Markdown 文档生成。
- 完整 Java 语义和类型解析。

## 使用方式

分析本地仓库：

```bash
javawiki analyze ./repo -o .nanobot-analysis
```

分析远端 Git 仓库：

```bash
javawiki analyze https://github.com/org/repo.git -o .nanobot-analysis
```

如果未安装 `javawiki` 命令，也可以在源码目录中使用：

```bash
PYTHONPATH=javawiki-analyzer python -m javawiki_analyzer.cli.main analyze ./repo -o .nanobot-analysis
```

Windows PowerShell：

```powershell
$env:PYTHONPATH='javawiki-analyzer'; python -m javawiki_analyzer.cli.main analyze .\repo -o .nanobot-analysis
```

## Git Submodule

远端仓库默认使用 `--submodules auto`。如果仓库包含 `.gitmodules`，分析器会执行递归 submodule 初始化：

```bash
javawiki analyze https://github.com/org/repo.git -o .nanobot-analysis --submodules auto
```

可选模式：

```bash
javawiki analyze <repo> -o .nanobot-analysis --submodules none
javawiki analyze <repo> -o .nanobot-analysis --submodules auto
javawiki analyze <repo> -o .nanobot-analysis --submodules recursive
```

本地仓库默认只检测 submodule 状态，不会修改用户工作区。如果希望初始化本地 submodule，需要显式传参：

```bash
javawiki analyze ./repo -o .nanobot-analysis --init-submodules
```

如果 submodule 缺失或更新失败，分析器会在 `analysis.json.diagnostics` 中写入结构化告警，例如：

- `submodule_init_required`
- `submodule_missing`
- `submodule_shallow_update_failed`
- `submodule_update_failed`
- `maven_module_path_missing`
- `java_source_not_found`
- `java_source_outside_maven_modules`

正式生成文档前应优先处理这些诊断，否则组件索引、依赖图、候选模块和最终文档都可能不完整。

## Java 源码扫描策略

分析器优先按 Maven module 的 `src/main/java/**/*.java` 扫描源码，然后补扫全仓库中尚未被 Maven module 覆盖的 `*.java` 文件。

为避免父 POM、submodule 或非标准仓库结构导致空结果，当前包含这些兜底策略：

- 父 POM 自身存在 `src/main/java` 时，也会作为一个可扫描模块。
- 对 Maven module 未覆盖的 Java 文件，会按最近的 `src/main/java` 前缀生成临时 source module。
- 全仓库补扫仍会应用 `--exclude` 排除规则，默认排除 `src/test/**`、`target/**` 和 `.git/**`。

如果最终仍然没有 Java 文件，会在 `analysis.json.diagnostics` 写入 `java_source_not_found`。
如果发现 Maven module 外的 Java 文件，会写入 `java_source_outside_maven_modules`，并把临时 source module 加入 `analysis.json.build_system.modules`。

## 主要输出

- `analysis.json`：分析入口、仓库信息、Maven 结构、submodule 状态、统计和诊断。
- `candidate_modules.json`：Maven、package、技术层、REST 入口等候选模块边界，供 agent 侧模型聚合使用。
- `module_tree.json`：规则兜底的最终模块树；agent 可在模型聚合后覆盖它。
- `processing_order.json`：模块文档生成顺序。
- `component_index.json`：紧凑组件索引。
- `dependencies.json`：组件级和模块级依赖图。
- `modules/*.json`：单个模块文档的主输入。
- `components/*.json`：单个 Java 组件详情和源码片段。

## 推荐流程

1. 使用 JavaWiki Analyzer 生成确定性分析产物。
2. 由 agent/skill 读取 `candidate_modules.json`，必要时使用模型聚合模块边界。
3. agent 将聚合后的 `module_tree.json`、`processing_order.json` 和 `modules/*.json` 写回分析目录。
4. agent 只读取最终 artifacts 生成 Markdown 文档。

这样可以避免在 analyzer 中重复实现模型调用，同时保证模型聚合结果可缓存、可复查、可 diff。
