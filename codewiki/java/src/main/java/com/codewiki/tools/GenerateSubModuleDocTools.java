package com.codewiki.tools;

import com.codewiki.config.AgentConcurrencyProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.service.SubModuleDocumentationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

@Component
public class GenerateSubModuleDocTools {

    private static final Logger log = LoggerFactory.getLogger(GenerateSubModuleDocTools.class);

    public static final String CTX_KEY = "executionContext";

    private final ExecutorService subAgentExecutor;
    private final Semaphore llmRateLimitSemaphore;
    private final AgentConcurrencyProperties concurrencyProps;
    private final SubModuleDocumentationService subModuleDocumentationService;

    public GenerateSubModuleDocTools(ExecutorService subAgentExecutor,
                                     Semaphore llmRateLimitSemaphore,
                                     AgentConcurrencyProperties concurrencyProps,
                                     SubModuleDocumentationService subModuleDocumentationService) {
        this.subAgentExecutor = subAgentExecutor;
        this.llmRateLimitSemaphore = llmRateLimitSemaphore;
        this.concurrencyProps = concurrencyProps;
        this.subModuleDocumentationService = subModuleDocumentationService;
    }

    @Tool(
            "Delegate documentation generation for a set of sub-modules to concurrent sub-agents. "
                    + "Each sub-module will be documented in its own module markdown file. "
                    + "You should provide sub-modules via 'subModuleRules' using concise grouping rules "
                    + "(e.g. 'package:com.foo', 'pattern:.*Controller\\.java$', 'file:path/to/File.java', 'remaining:'). "
                    + "Only use 'explicitSubModuleSpecs' when the split cannot be expressed cleanly with rules. "
                    + "Returns a summary of generated files and failures."
    )
    public String generateSubModuleDocumentation(
            @ToolParam(description = "Map of sub-module name -> grouping rule. Preferred. "
                    + "Supported rules: 'package:prefix', 'pattern:regex', 'file:relative/path.java', 'remaining:'")
            Map<String, String> subModuleRules,
            @ToolParam(description = "Optional map of sub-module name -> explicit list of component IDs. "
                    + "ONLY use this when rules cannot cleanly express the split.")
            Map<String, List<String>> explicitSubModuleSpecs,
            ToolContext toolContext) {

        ModuleExecutionContext parentContext = ReadCodeComponentsTools.extractContext(toolContext);
        if (parentContext.hasReachedMaxDepth()) {
            log.warn("[{}] Max depth {} reached, sub-agent delegation skipped",
                    parentContext.getModuleName(), parentContext.getMaxDepth());
            return "Max recursion depth reached. Sub-modules not generated.";
        }

        Map<String, List<String>> resolvedSpecs = resolveSubModuleSpecs(
                subModuleRules, explicitSubModuleSpecs, parentContext);

        if (resolvedSpecs.isEmpty()) {
            return "No sub-modules specified after resolving rules and explicit specs.";
        }

        parentContext.getModuleTreeManager().registerSubModules(
                parentContext.getModulePath(), resolvedSpecs);

        List<CompletableFuture<SubAgentResult>> futures = new ArrayList<CompletableFuture<SubAgentResult>>();
        for (Map.Entry<String, List<String>> entry : resolvedSpecs.entrySet()) {
            final String subModuleName = entry.getKey();
            final ModuleExecutionContext subContext =
                    parentContext.forSubModule(subModuleName, entry.getValue());

            futures.add(CompletableFuture.supplyAsync(
                    () -> executeSubAgent(subModuleName, subContext),
                    subAgentExecutor
            ));
        }

        List<String> succeeded = new ArrayList<String>();
        List<String> failed = new ArrayList<String>();

        for (CompletableFuture<SubAgentResult> future : futures) {
            try {
                SubAgentResult result = future.get();
                if (result.success) {
                    succeeded.add(result.moduleName);
                } else {
                    failed.add(result.moduleName + "(" + result.errorMessage + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed.add("interrupted");
            } catch (ExecutionException e) {
                log.error("Unexpected execution error in sub-agent future", e);
                failed.add("execution-error: " + e.getCause().getMessage());
            }
        }

        return buildSummary(succeeded, failed);
    }

    /**
     * Resolves rule-based specs first, then explicit specs, enforcing mutual exclusivity.
     * Each component ID is assigned to at most one sub-module (first match wins).
     */
    private Map<String, List<String>> resolveSubModuleSpecs(
            Map<String, String> subModuleRules,
            Map<String, List<String>> explicitSubModuleSpecs,
            ModuleExecutionContext parentContext) {

        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        Set<String> assigned = new LinkedHashSet<String>();
        List<String> coreComponentIds = parentContext.getCoreComponentIds();
        Map<String, Node> components = parentContext.getComponents();

        // ── Phase 1: resolve rules in declaration order ─────────────────────────
        if (subModuleRules != null) {
            for (Map.Entry<String, String> entry : subModuleRules.entrySet()) {
                String subModuleName = entry.getKey();
                String rule = entry.getValue() == null ? "" : entry.getValue().trim();
                if (rule.isEmpty()) {
                    log.warn("Empty rule for sub-module '{}', skipping", subModuleName);
                    continue;
                }

                List<String> matched = new ArrayList<String>();
                for (String compId : coreComponentIds) {
                    if (assigned.contains(compId)) {
                        continue;
                    }
                    if (matchesRule(compId, components.get(compId), rule)) {
                        matched.add(compId);
                        assigned.add(compId);
                    }
                }

                if (!matched.isEmpty()) {
                    result.put(subModuleName, matched);
                    log.info("Rule '{}' matched {} components for sub-module '{}'",
                            rule, matched.size(), subModuleName);
                } else {
                    log.warn("Rule '{}' matched no components for sub-module '{}'", rule, subModuleName);
                }
            }
        }

        // ── Phase 2: resolve explicit specs with deduplication ──────────────────
        if (explicitSubModuleSpecs != null) {
            List<String> duplicates = new ArrayList<String>();
            for (Map.Entry<String, List<String>> entry : explicitSubModuleSpecs.entrySet()) {
                String subModuleName = entry.getKey();
                List<String> ids = entry.getValue();
                if (ids == null || ids.isEmpty()) {
                    continue;
                }

                List<String> uniqueIds = new ArrayList<String>();
                for (String compId : ids) {
                    if (assigned.contains(compId)) {
                        duplicates.add(compId + " (removed from " + subModuleName + ")");
                    } else {
                        uniqueIds.add(compId);
                        assigned.add(compId);
                    }
                }

                if (!uniqueIds.isEmpty()) {
                    result.merge(subModuleName, uniqueIds, (oldList, newList) -> {
                        List<String> merged = new ArrayList<String>(oldList);
                        merged.addAll(newList);
                        return merged;
                    });
                }
            }

            if (!duplicates.isEmpty()) {
                log.warn("Deduplicated overlapping explicit component IDs: {}", duplicates);
            }
        }

        return result;
    }

    private boolean matchesRule(String compId, Node node, String rule) {
        if (rule.startsWith("package:")) {
            String prefix = rule.substring("package:".length());
            if (node != null) {
                if (node.getClassFqn() != null && node.getClassFqn().startsWith(prefix)) {
                    return true;
                }
                for (String pkg : node.getPackageFqns()) {
                    if (pkg.equals(prefix) || pkg.startsWith(prefix + ".")) {
                        return true;
                    }
                }
            }
            return compId.startsWith(prefix);
        }

        if (rule.startsWith("pattern:")) {
            String regex = rule.substring("pattern:".length());
            try {
                return Pattern.matches(regex, compId);
            } catch (PatternSyntaxException e) {
                log.warn("Invalid regex pattern '{}': {}", regex, e.getMessage());
                return false;
            }
        }

        if (rule.startsWith("file:")) {
            String filePath = rule.substring("file:".length());
            if (node != null && filePath.equals(node.getRelativePath())) {
                return true;
            }
            // component IDs are often "relative/path::SymbolName"
            int sep = compId.indexOf("::");
            String filePart = sep >= 0 ? compId.substring(0, sep) : compId;
            return filePath.equals(filePart);
        }

        if (rule.equals("remaining:") || rule.equals("remaining")) {
            return true; // will only see unassigned IDs because caller checks assigned set first
        }

        log.warn("Unrecognized sub-module rule '{}', treating as no-match", rule);
        return false;
    }

    private SubAgentResult executeSubAgent(String subModuleName, ModuleExecutionContext subContext) {
        boolean permitAcquired = false;
        try {
            permitAcquired = llmRateLimitSemaphore.tryAcquire(
                    concurrencyProps.getRateLimitTimeoutMinutes(), TimeUnit.MINUTES);

            if (!permitAcquired) {
                String msg = "Timed out waiting for LLM rate-limit permit after "
                        + concurrencyProps.getRateLimitTimeoutMinutes() + " minutes";
                return SubAgentResult.failure(subModuleName, msg);
            }

            subModuleDocumentationService.generate(subContext);
            return SubAgentResult.success(subModuleName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SubAgentResult.failure(subModuleName, "Interrupted while waiting for rate-limit permit");
        } catch (Exception e) {
            log.error("Sub-agent failed for module '{}': {}", subModuleName, e.getMessage(), e);
            return SubAgentResult.failure(subModuleName, e.getMessage());
        } finally {
            if (permitAcquired) {
                llmRateLimitSemaphore.release();
            }
        }
    }

    private String buildSummary(List<String> succeeded, List<String> failed) {
        StringBuilder sb = new StringBuilder();
        if (!succeeded.isEmpty()) {
            String files = succeeded.stream()
                    .map(n -> n + ".md")
                    .collect(Collectors.joining(", "));
            sb.append("Generated: ").append(files).append(".");
        }
        if (!failed.isEmpty()) {
            sb.append(" Failed: ").append(String.join(", ", failed)).append(".");
        }
        return sb.length() == 0 ? "No sub-modules were processed." : sb.toString();
    }

    private static final class SubAgentResult {
        private final String moduleName;
        private final boolean success;
        private final String errorMessage;

        private SubAgentResult(String moduleName, boolean success, String errorMessage) {
            this.moduleName = moduleName;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        private static SubAgentResult success(String name) {
            return new SubAgentResult(name, true, null);
        }

        private static SubAgentResult failure(String name, String error) {
            return new SubAgentResult(name, false, error);
        }
    }
}
