Generate comprehensive documentation for the {module_name} module using the provided module context and summarized core component information.

<MODULE_CONTEXT>
{module_context}
</MODULE_CONTEXT>
* NOTE: You can refer the other modules in the module tree based on the dependencies between their core components to make the documentation more structured and avoid repeating the same information. Know that all documentation files are saved in the same folder not structured as module tree. e.g. [alt text]([ref_module_name].md)

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

Rules:
1. Treat core component summaries as the compressed substitute for raw core component code.
2. Use module brief for module-level understanding and core component summaries for component-level understanding.
3. Use recall_summary first when more detail is needed; use read_code_components only when direct source evidence is required.
4. If source evidence conflicts with summaries, source evidence wins.
5. Prefer linking to other module documents instead of duplicating details.
