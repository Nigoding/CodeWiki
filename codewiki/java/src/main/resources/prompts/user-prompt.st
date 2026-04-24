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
3. 需要方法级细节时先用 `recall_summary`；若上下文已给出某类下面的 FQN 与方法签名，可基于“类 FQN + 方法签名”按需获取单个方法摘要；方法签名留空时可召回该类已知方法摘要。
4. 源码证据与摘要冲突时以源码为准，并在文档中指出冲突点。
5. `<CONTEXT_GAPS>` 只表示基础上下文缺口；为空时不要读取源码补全背景信息，但仍可为了数据流、组件交互、输入输出、副作用、状态变化或流程图按需调用 `recall_summary`。
6. 所有生成的文档文件都保存在同一目录，不按模块树层级分目录；引用其他模块文档统一使用 `[ref_module_name](ref_module_name.md)` 相对链接格式。
7. 可参考模块树中其他模块的依赖关系来组织文档结构，避免重复介绍共用概念，改为链接过去。
