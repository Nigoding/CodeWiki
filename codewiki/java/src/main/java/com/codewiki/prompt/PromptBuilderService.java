package com.codewiki.prompt;

import com.codewiki.config.PromptContextProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.summary.ModuleBriefBuilder;
import com.codewiki.summary.ModuleSummaryContext;
import com.codewiki.summary.ModuleSummaryContextLoader;
import com.codewiki.summary.SummaryFormatter;
import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummaryRecord;
import com.codewiki.summary.dto.ModuleBrief;
import com.codewiki.util.Texts;
import com.codewiki.util.TokenCounter;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the system and user prompts that are sent to the LLM.
 *
 * Template files live under src/main/resources/prompts/ and are loaded via
 * Spring's @Value("classpath:...") mechanism.  This keeps prompt text out of
 * Java source code and allows non-developer edits without a recompile.
 *
 * The formatting logic (module tree serialisation, component code assembly)
 * mirrors the Python format_user_prompt() function but is expressed as
 * plain Java methods rather than string concatenation scattered across the
 * codebase.
 */
@Component
public class PromptBuilderService {

    private static final Map<String, String> EXTENSION_TO_LANGUAGE;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("py",   "python");
        m.put("md",   "markdown");
        m.put("sh",   "bash");
        m.put("json", "json");
        m.put("yaml", "yaml");
        m.put("yml",  "yaml");
        m.put("java", "java");
        m.put("js",   "javascript");
        m.put("ts",   "typescript");
        m.put("tsx",  "typescript");
        m.put("jsx",  "javascript");
        m.put("cpp",  "cpp");
        m.put("c",    "c");
        m.put("h",    "c");
        m.put("hpp",  "cpp");
        m.put("cs",   "csharp");
        m.put("kt",   "kotlin");
        m.put("php",  "php");
        EXTENSION_TO_LANGUAGE = Collections.unmodifiableMap(m);
    }

    @Value("classpath:prompts/system-complex.st")
    private Resource complexSystemTemplate;

    @Value("classpath:prompts/system-leaf.st")
    private Resource leafSystemTemplate;

    @Value("classpath:prompts/user-prompt.st")
    private Resource userPromptTemplate;

    private final TokenCounter tokenCounter;
    private final ModuleSummaryContextLoader summaryContextLoader;
    private final ModuleBriefBuilder moduleBriefBuilder;
    private final PromptContextProperties promptContextProperties;
    private final SummaryFormatter summaryFormatter;

    public PromptBuilderService(TokenCounter tokenCounter,
                                ModuleSummaryContextLoader summaryContextLoader,
                                ModuleBriefBuilder moduleBriefBuilder,
                                PromptContextProperties promptContextProperties,
                                SummaryFormatter summaryFormatter) {
        this.tokenCounter = tokenCounter;
        this.summaryContextLoader = summaryContextLoader;
        this.moduleBriefBuilder = moduleBriefBuilder;
        this.promptContextProperties = promptContextProperties;
        this.summaryFormatter = summaryFormatter;
    }

    // ── public API ────────────────────────────────────────────────────────────

    public String buildComplexSystemPrompt(ModuleExecutionContext ctx) {
        Map<String, Object> vars = systemVars(ctx);
        return new PromptTemplate(complexSystemTemplate).render(vars);
    }

    public String buildLeafSystemPrompt(ModuleExecutionContext ctx) {
        Map<String, Object> vars = systemVars(ctx);
        return new PromptTemplate(leafSystemTemplate).render(vars);
    }

    public String buildUserPrompt(ModuleExecutionContext ctx) {
        ModuleBrief brief = moduleBriefBuilder.build(ctx);
        Map<String, Object> vars = new HashMap<>();
        vars.put("module_name", ctx.getModuleName());
        vars.put("module_context", renderModuleContext(ctx));
        vars.put("module_brief", renderModuleBrief(brief));
        vars.put("core_component_summaries", renderCoreComponentSummaries(ctx));
        vars.put("context_gaps", renderContextGaps(ctx, brief));
        vars.put("core_components", buildFallbackSourceContext(ctx, brief));
        return new PromptTemplate(userPromptTemplate).render(vars);
    }

    /**
     * Returns the approximate token count for the core-component portion of the
     * user prompt.  Used by ModuleComplexityEvaluator to decide agent type.
     */
    public int countCoreComponentTokens(ModuleExecutionContext ctx) {
        ModuleBrief brief = moduleBriefBuilder.build(ctx);
        if (promptContextProperties.isPreferSummaryContext() && brief.isSummaryBacked()) {
            return tokenCounter.count(renderModuleContext(ctx))
                    + tokenCounter.count(renderModuleBrief(brief))
                    + tokenCounter.count(renderCoreComponentSummaries(ctx))
                    + tokenCounter.count(renderContextGaps(ctx, brief));
        }
        return tokenCounter.count(formatCoreComponentsWithinTokenBudget(
                ctx,
                promptContextProperties.getFallbackSourceTokens()));
    }

    // ── module tree formatting ────────────────────────────────────────────────

    /**
     * Converts the module tree map into an indented text representation.
     * Marks the current module with "(current module)" to help the LLM orient itself.
     */
    @SuppressWarnings("unchecked")
    public String formatModuleTree(Map<String, Object> tree, String currentModuleName) {
        List<String> lines = new ArrayList<>();
        formatModuleTreeRecursive(tree, currentModuleName, 0, lines);
        return String.join("\n", lines);
    }

    @SuppressWarnings("unchecked")
    private void formatModuleTreeRecursive(Map<String, Object> node,
                                            String currentModuleName,
                                            int indent,
                                            List<String> lines) {
        String pad = repeat("  ", indent);
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> value = (Map<String, Object>) entry.getValue();

            String label = key.equals(currentModuleName) ? key + " (current module)" : key;
            lines.add(pad + label);

            // Group component IDs by file path for readability
            List<String> components = (List<String>) value.get("components");
            if (components != null) {
                Map<String, List<String>> byFile = new LinkedHashMap<>();
                for (String compId : components) {
                    int sep = compId.indexOf("::");
                    String file = sep >= 0 ? compId.substring(0, sep) : "";
                    String name = sep >= 0 ? compId.substring(sep + 2) : compId;
                    byFile.computeIfAbsent(file, k -> new ArrayList<>()).add(name);
                }
                for (Map.Entry<String, List<String>> fe : byFile.entrySet()) {
                    if (!fe.getKey().isEmpty()) {
                        lines.add(pad + "  " + fe.getKey() + ": " + String.join(", ", fe.getValue()));
                    } else {
                        lines.add(pad + "  " + String.join(", ", fe.getValue()));
                    }
                }
            }

            Map<String, Object> children = (Map<String, Object>) value.get("children");
            if (children != null && !children.isEmpty()) {
                lines.add(pad + "  Children:");
                formatModuleTreeRecursive(children, currentModuleName, indent + 2, lines);
            }
        }
    }

    // ── core component code formatting ────────────────────────────────────────

    /**
     * Groups core components by source file and builds a markdown-fenced
     * representation of each file's content.  Mirrors Python format_user_prompt().
     */
    private String formatCoreComponents(ModuleExecutionContext ctx) {
        return formatCoreComponentsWithinTokenBudget(ctx, Integer.MAX_VALUE);
    }

    private String formatCoreComponentsWithinTokenBudget(ModuleExecutionContext ctx, int maxTokens) {
        // Group component IDs by their source file path
        Map<String, List<String>> byFile = new LinkedHashMap<>();
        for (String compId : ctx.getCoreComponentIds()) {
            Node node = ctx.getComponents().get(compId);
            if (node == null) continue;
            byFile.computeIfAbsent(node.getRelativePath(), k -> new ArrayList<>()).add(compId);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : byFile.entrySet()) {
            int currentTokens = tokenCounter.count(sb.toString());
            if (currentTokens >= maxTokens) {
                break;
            }

            String relativePath = entry.getKey();
            List<String> idsInFile = entry.getValue();
            StringBuilder section = new StringBuilder();

            section.append("# File: ").append(relativePath).append("\n\n");
            section.append("## Core Components in this file:\n");
            for (String id : idsInFile) {
                section.append("- ").append(id).append("\n");
            }

            // Determine language from file extension
            String ext = "";
            int dot = relativePath.lastIndexOf('.');
            if (dot >= 0) {
                ext = relativePath.substring(dot + 1).toLowerCase();
            }
            String lang = EXTENSION_TO_LANGUAGE.getOrDefault(ext, "");

            section.append("\n## File Content:\n```").append(lang).append("\n");

            // Read the actual file content from the first component
            Node firstNode = ctx.getComponents().get(idsInFile.get(0));
            if (firstNode != null && firstNode.getContent() != null) {
                section.append(limitTextToTokens(
                        firstNode.getContent(),
                        Math.max(0, maxTokens - currentTokens - 30)));
            } else {
                section.append("// Error: file content not available");
            }
            section.append("\n```\n\n");

            if (tokenCounter.count(sb.toString() + section.toString()) > maxTokens) {
                break;
            }
            sb.append(section);
        }
        return sb.toString();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> systemVars(ModuleExecutionContext ctx) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("module_name", ctx.getModuleName());
        String ci = ctx.getCustomInstructions();
        vars.put("custom_instructions",
                ci != null && !ci.isEmpty()
                        ? "\n\n<CUSTOM_INSTRUCTIONS>\n" + ci + "\n</CUSTOM_INSTRUCTIONS>"
                        : "");
        return vars;
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    private String renderModuleContext(ModuleExecutionContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("- Module: ").append(ctx.getModuleName()).append("\n");
        List<String> coreNames = collectCoreComponentNames(ctx);
        if (!coreNames.isEmpty()) {
            sb.append("- Core components: ").append(String.join(", ", coreNames)).append("\n");
        }
        sb.append("- Module tree:\n")
                .append(formatModuleTree(ctx.getModuleTreeManager().getReadOnlySnapshot(), ctx.getModuleName()));
        return sb.toString().trim();
    }

    private String renderModuleBrief(ModuleBrief brief) {
        StringBuilder sb = new StringBuilder();
        sb.append("- Module purpose: ").append(brief.getModulePurpose()).append("\n");

        if (Texts.trimToEmpty(brief.getBusinessValue()).length() > 0) {
            sb.append("- Business value: ").append(brief.getBusinessValue()).append("\n");
        }

        if (!brief.getMainResponsibilities().isEmpty()) {
            sb.append("- Main responsibilities:\n");
            for (String responsibility : brief.getMainResponsibilities()) {
                sb.append("  - ").append(responsibility).append("\n");
            }
        }

        if (!brief.getKeyComponents().isEmpty()) {
            sb.append("- Key components: ")
                    .append(String.join(", ", brief.getKeyComponents()))
                    .append("\n");
        }

        if (!brief.getMajorDependencies().isEmpty()) {
            sb.append("- Major dependencies: ")
                    .append(String.join(", ", brief.getMajorDependencies()))
                    .append("\n");
        }

        if (!brief.getMajorSideEffects().isEmpty()) {
            sb.append("- Major side effects: ")
                    .append(String.join(", ", brief.getMajorSideEffects()))
                    .append("\n");
        }

        if (!brief.getOpenQuestions().isEmpty()) {
            sb.append("- Open questions:\n");
            for (String q : brief.getOpenQuestions()) {
                sb.append("  - ").append(q).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String renderCoreComponentSummaries(ModuleExecutionContext ctx) {
        if (!promptContextProperties.isPreferSummaryContext()) {
            return "";
        }

        ModuleSummaryContext summaryCtx = resolveSummaryContext(ctx);

        StringBuilder sb = new StringBuilder();
        for (String componentId : ctx.getCoreComponentIds()) {
            Node node = ctx.getComponents().get(componentId);
            if (node == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }

            ClassSummaryRecord classRecord = resolveClassSummary(node, summaryCtx);
            if (classRecord != null) {
                sb.append(summaryFormatter.formatCoreComponentSummary(
                        classRecord,
                        selectRepresentativeMethods(node, summaryCtx)));
            } else {
                sb.append("- Component: ")
                        .append(Texts.trimToEmpty(node.getName()))
                        .append("\n  Summary coverage: class summary missing; use recall_summary if this component becomes important.");
            }
        }
        return sb.toString().trim();
    }

    private String renderContextGaps(ModuleExecutionContext ctx, ModuleBrief brief) {
        List<String> gaps = new ArrayList<String>();
        for (String question : brief.getOpenQuestions()) {
            String value = Texts.trimToEmpty(question);
            if (!value.isEmpty()) {
                gaps.add(value);
            }
        }

        ModuleSummaryContext summaryCtx = resolveSummaryContext(ctx);
        for (String componentId : ctx.getCoreComponentIds()) {
            Node node = ctx.getComponents().get(componentId);
            if (node == null) {
                continue;
            }
            ClassSummaryRecord classRecord = resolveClassSummary(node, summaryCtx);
            if (classRecord == null) {
                gaps.add("No class summary is available for core component " + Texts.trimToEmpty(node.getName()) + ".");
                continue;
            }
            if (selectRepresentativeMethods(node, summaryCtx).isEmpty()) {
                gaps.add("Representative method behavior is missing for core component " + classRecord.getClassName() + ".");
            }
        }

        if (gaps.isEmpty()) {
            return "- Current summaries cover the main core components. Use recall_summary first if more detail is needed.";
        }

        StringBuilder sb = new StringBuilder();
        for (String gap : gaps) {
            sb.append("- ").append(gap).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildFallbackSourceContext(ModuleExecutionContext ctx, ModuleBrief brief) {
        if (promptContextProperties.isPreferSummaryContext() && brief.isSummaryBacked()) {
            return "";
        }
        return formatCoreComponentsWithinTokenBudget(ctx, promptContextProperties.getFallbackSourceTokens());
    }

    private String limitTextToTokens(String text, int maxTokens) {
        if (text == null || text.isEmpty() || maxTokens <= 0) {
            return "";
        }
        if (tokenCounter.count(text) <= maxTokens) {
            return text;
        }
        int maxChars = Math.max(64, maxTokens * 4);
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n// [truncated source context]";
    }

    private ModuleSummaryContext resolveSummaryContext(ModuleExecutionContext ctx) {
        ModuleSummaryContext summaryCtx = ctx.getSummaryContext();
        if (summaryCtx == null) {
            summaryCtx = summaryContextLoader.load(ctx);
        }
        return summaryCtx;
    }

    private List<String> collectCoreComponentNames(ModuleExecutionContext ctx) {
        List<String> names = new ArrayList<String>();
        for (String componentId : ctx.getCoreComponentIds()) {
            Node node = ctx.getComponents().get(componentId);
            if (node == null) {
                continue;
            }
            String name = Texts.trimToEmpty(node.getName());
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private ClassSummaryRecord resolveClassSummary(Node node, ModuleSummaryContext summaryCtx) {
        String classFqn = Texts.trimToEmpty(node.getClassFqn());
        return classFqn.isEmpty() ? null : summaryCtx.getClassSummary(classFqn);
    }

    private List<MethodSummaryRecord> selectRepresentativeMethods(Node node, ModuleSummaryContext summaryCtx) {
        String classFqn = Texts.trimToEmpty(node.getClassFqn());
        if (classFqn.isEmpty()) {
            return Collections.emptyList();
        }
        List<MethodSummaryRecord> methods = summaryCtx.getMethodSummariesByClass(classFqn);
        if (methods.isEmpty()) {
            return Collections.emptyList();
        }
        return methods.subList(0, Math.min(2, methods.size()));
    }

}
