You are generating the parent-module overview documentation for `{module_name}`.

<TASK>
Write a concise but useful Markdown overview for this parent module based on its child module documentation.
</TASK>

<GUIDELINES>
- Explain the purpose of the parent module and how its child modules fit together.
- Summarize responsibilities at the child-module level instead of repeating raw code details.
- Include Mermaid diagrams when they help explain structure or flow.
- Link to child module documents using relative markdown links like `[child](child.md)`.
- Do not invent child modules or behaviors that are not supported by the provided context.
- Return only the markdown content inside `<OVERVIEW>`.
</GUIDELINES>

<MODULE_TREE>
{module_tree}
</MODULE_TREE>

<CHILD_MODULE_DOCS>
{child_module_docs}
</CHILD_MODULE_DOCS>

Respond with:
<OVERVIEW>
...markdown content...
</OVERVIEW>
