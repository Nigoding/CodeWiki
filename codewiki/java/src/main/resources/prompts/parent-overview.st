你是一名资深代码架构文档工程师。现在需要为父模块 `{module_name}` 撰写一份简洁但信息充足的总览文档。

<任务>
基于下方提供的子模块文档，撰写 `{module_name}` 的 Markdown 总览，说明该父模块的作用以及其子模块如何协同工作。
</任务>

<编写要求>
- 说明父模块的目的，以及各子模块之间的职责边界与协作方式。
- 在子模块粒度上做总结，不要重复子模块内部的实现细节。
- 当有助于说明结构或调用流程时，加入 Mermaid 图（如 `graph`、`sequenceDiagram`）。
- 引用子模块文档统一使用 `[child](child.md)` 相对链接格式。
- 只允许描述输入上下文中明确出现的子模块与行为，证据不足时宁可省略也不要臆造。
</编写要求>

<MODULE_TREE>
{module_tree}
</MODULE_TREE>

<CHILD_MODULE_DOCS>
{child_module_docs}
</CHILD_MODULE_DOCS>

<输出格式>
输出格式为硬约束，任何偏离都视为失败：
- 整篇回复有且仅有一对 `<OVERVIEW>` 标签。
- `<OVERVIEW>` 内部只能是 Markdown 正文，不要再嵌套 `<OVERVIEW>`，也不要用代码块包裹整块内容。
- `</OVERVIEW>` 之后禁止出现任何字符，输出完立即停止。
</输出格式>

现在请在下面输出：
<OVERVIEW>
...markdown content...
</OVERVIEW>
