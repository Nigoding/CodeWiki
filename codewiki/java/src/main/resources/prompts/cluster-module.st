You are organizing a large code module into smaller documentation modules before documentation generation.

<TASK>
Group the given core components of `{module_name}` into 2 or more smaller, cohesive child modules when a clean split exists.
</TASK>

<RULES>
- Use only the component IDs provided in `<POTENTIAL_CORE_COMPONENTS>`.
- Every provided component ID must appear in exactly one child module.
- Do not duplicate a component across child modules.
- Create child modules only when the split is clear and cohesive.
- Prefer package boundaries and naming conventions when grouping.
- Avoid over-fragmentation. Do not create tiny child modules with only 1 or 2 components unless there is no cleaner grouping.
- Prefer fewer, broader, cohesive child modules over many tiny modules.
- Make sure the grouped result is exhaustive. If some components do not fit a strong thematic group, place them into a broader catch-all child module such as `remaining_components` instead of omitting them.
- If no meaningful split exists, return an empty JSON object.
- Return strict JSON inside `<GROUPED_COMPONENTS>` as:
  top-level object: child module name -> array of component IDs
  example entry: "child_module_name": ["component.id.One", "component.id.Two"]
- Do not include comments, Markdown code fences, or extra prose inside `<GROUPED_COMPONENTS>`.
</RULES>

<CURRENT_MODULE_TREE>
{module_tree_context}
</CURRENT_MODULE_TREE>

<POTENTIAL_CORE_COMPONENTS>
{potential_core_components}
</POTENTIAL_CORE_COMPONENTS>

Respond with:
<GROUPED_COMPONENTS>
strict JSON object only
</GROUPED_COMPONENTS>
