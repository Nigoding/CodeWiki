---
name: codewiki
description: >
  使用 CodeWiki MCP 的代码分析结果，为代码仓库生成分层 wiki 文档。
  当用户要求文档化代码仓库、生成架构文档、解释模块关系、创建代码 wiki，
  或重新生成已有代码 wiki 时使用。
---

# CodeWiki 分层仓库文档生成

CodeWiki 将职责严格拆为两部分：

- **CodeWiki MCP**：完成 AST 分析、依赖图构建、模块聚类和递归拆分，返回最终稳定的模块树。
- **当前 Agent**：按分析结果读取代码、撰写文档、建立链接并验证输出。

生成过程中不得自行拆分模块、修改模块树或启动子模块生成 Agent。模块划分不合适时，要求 MCP 重新分析或聚类。

## 开始前

确定以下输入：

- 目标仓库绝对路径。
- 输出目录；用户未指定时使用仓库内的 `docs/`。
- 可选的包含/排除规则、文档类型、关注模块和自定义要求。

CodeWiki MCP 必须提供 [MCP 契约](references/mcp-contract.md) 中定义的分析与按需读取能力。

## 工作流

### 1. 获取稳定分析结果

调用 `analyze_repository`。默认启用完整递归聚类，使 MCP 一次性返回最终模块树。

确认响应至少包含：

- `analysis_id`
- `repo_name`
- `component_index`
- `module_tree`
- `processing_order`
- `diagnostics`

将 `diagnostics` 中的错误和降级信息告知用户。存在无法解析的核心源码时，不要静默生成看似完整的文档。

### 2. 检查生成计划

生成前验证：

- `module_tree` 中每个模块具有唯一 `module_id` 和唯一 `doc_path`。
- 每个模块引用的组件存在于 `component_index`。
- `processing_order` 覆盖所有模块，且子模块始终早于父模块。
- 所有 `doc_path` 位于输出目录内。

`processing_order` 只包含模块，不包含仓库根总览。完成所有模块后再单独生成 `overview.md`。

若 `module_tree` 为空，跳过模块文档，直接以仓库组件生成 `overview.md`。

### 3. 按顺序生成模块文档

严格按照 `processing_order` 逐个处理模块。

#### 叶子模块

1. 调用 `get_module_context` 获取模块组件、内部依赖、外部依赖和相关源码。
2. 信息不足时使用 `get_components` 批量补充组件源码。
3. 按 [模块文档模板](references/module-document-template.md) 的叶子模块规则写入模块的 `doc_path`。
4. 只描述能够由分析结果或源码支持的行为；不根据命名猜测实现。

#### 父模块

1. 读取其直接子模块的文档摘要；仅在需要确认跨模块关系时读取完整子文档。
2. 调用 `get_module_context` 获取跨子模块依赖和父模块级上下文。
3. 按 [模块文档模板](references/module-document-template.md) 的父模块规则写入模块的 `doc_path`。
4. 保持概述级别，链接子模块文档，不重复其组件级内容。

每次写入 Markdown 后，按照 [Mermaid 规则](references/mermaid-rules.md) 检查并修复图表。

### 4. 生成仓库总览

所有模块完成后，按 [总览模板](references/overview-template.md) 生成 `overview.md`。

总览必须：

- 说明仓库用途和系统边界。
- 展示端到端高层架构。
- 引用每个顶层模块文档。
- 总结关键数据流、入口和外部依赖。

### 5. 验证输出

完成前检查：

- `overview.md` 和所有模块 `doc_path` 均存在。
- 所有内部 Markdown 链接指向存在的文件。
- 所有 Mermaid 图语法有效，节点与关系有源码或分析依据。
- 父模块没有大段复制子模块内容。
- 文档没有引用分析结果中不存在的组件、API 或流程。
- 输出目录之外没有新增或修改文件。

简要报告生成文件、分析降级项和未能确认的内容。

## 失败处理

- MCP 工具不可用：停止生成并说明缺少的工具。
- 模块树不稳定、存在重复路径或顺序无效：要求 MCP 重新分析，不在生成阶段修补模块树。
- 单个组件源码过大：使用 `get_components` 分批读取，只加载当前文档所需内容。
- 模块缺少足够证据：明确记录限制，避免填充推测性说明。
