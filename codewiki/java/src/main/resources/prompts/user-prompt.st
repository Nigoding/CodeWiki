Generate comprehensive documentation for the {module_name} module using the provided module tree and structured summaries.

<MODULE_TREE>
{module_tree}
</MODULE_TREE>
* NOTE: You can refer the other modules in the module tree based on the dependencies between their core components to make the documentation more structured and avoid repeating the same information. Know that all documentation files are saved in the same folder not structured as module tree. e.g. [alt text]([ref_module_name].md)

<MODULE_BRIEF>
{module_brief}
</MODULE_BRIEF>

<KEY_SUMMARIES>
{key_summaries}
</KEY_SUMMARIES>

<OPTIONAL_SOURCE_CONTEXT>
{core_components}
</OPTIONAL_SOURCE_CONTEXT>

Rules:
1. Treat structured summaries as the primary knowledge source.
2. Use source-reading tools only when behavior details, branching logic, side effects, or dependencies are unclear.
3. If structured summaries conflict with source evidence, source evidence wins.
4. Prefer linking to other module documents instead of duplicating details.
