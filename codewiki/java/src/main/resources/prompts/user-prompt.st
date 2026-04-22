基于下列上下文，为模块 {module_name} 生成文档。

<MODULE_CONTEXT>
{module_context}
</MODULE_CONTEXT>

<MODULE_BRIEF>
{module_brief}
</MODULE_BRIEF>

<CORE_COMPONENT_SUMMARIES>
{core_component_summaries}
</CORE_COMPONENT_SUMMARIES>

<CONTEXT_GAPS>
{context_gaps}
</CONTEXT_GAPS>

<OPTIONAL_SOURCE_CONTEXT>
{core_components}
</OPTIONAL_SOURCE_CONTEXT>

规则：
1. `<CORE_COMPONENT_SUMMARIES>` 是核心组件源码的压缩替代，优先使用，不要重复请求源码。
2. `<MODULE_BRIEF>` 用于模块级理解，`<CORE_COMPONENT_SUMMARIES>` 用于组件级理解，二者角色不可互换。
3. 需要方法级细节时先用 `recall_summary`；必须看源码证据时才用 `read_code_components`。
4. 源码证据与摘要冲突时以源码为准，并在文档中指出冲突点。
5. 若 `<CONTEXT_GAPS>` 为空，视为输入已完整，不要调用任何工具，直接进入写作。
6. 所有生成的文档文件都保存在同一目录，不按模块树层级分目录；引用其他模块文档统一使用 `[ref_module_name](ref_module_name.md)` 相对链接格式。
7. 可参考模块树中其他模块的依赖关系来组织文档结构，避免重复介绍共用概念，改为链接过去。
