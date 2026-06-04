# Mermaid 规则

仅在图表能够提高理解时使用 Mermaid。不要为了每个章节都拥有图表而创建重复或无信息量的图。

## 图表选择

- 模块或组件静态关系：`flowchart` 或 `classDiagram`
- 请求、事件和调用顺序：`sequenceDiagram`
- 状态变化：`stateDiagram-v2`
- 决策和处理步骤：`flowchart`

## 编写规则

- 节点 ID 使用简短 ASCII 标识符，例如 `auth_service`。
- 展示名称放在标签中，例如 `auth_service["Auth Service"]`。
- 同一张图保持单一抽象层级。
- 父模块图使用直接子模块作为节点。
- 叶子模块图使用核心组件作为节点。
- 只画分析结果或源码能够证明的关系。
- 大型图拆成多个聚焦图，避免超过约 15 个主要节点。

## 验证流程

每次创建或修改 Markdown 后：

1. 检查所有 Mermaid fenced code block 是否闭合。
2. 使用可用的 Mermaid 校验工具解析每张图。
3. 修复所有语法错误后重新校验。
4. 检查图中节点、箭头和标签是否与正文一致。
5. 校验工具不可用时，明确报告未完成自动校验。

## 常见问题

- 节点 ID 含空格、点号、斜杠或括号：改用 ASCII ID，并把原名称放入标签。
- 标签包含特殊字符：使用引号包裹标签。
- `sequenceDiagram` 参与者名称复杂：使用 `participant short as Display Name`。
- 图表关系过于密集：只保留架构主干，将细节放入正文。

