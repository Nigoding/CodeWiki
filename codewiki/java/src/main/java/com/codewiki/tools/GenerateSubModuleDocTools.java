package com.codewiki.tools;

import com.codewiki.config.AgentConcurrencyProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.service.SubModuleDocumentationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
                    + "subModuleSpecs must be a map of sub-module name to the list of component IDs that belong to it. "
                    + "Returns a summary of generated files and failures."
    )
    public String generateSubModuleDocumentation(
            @ToolParam(description = "Map of sub-module name -> list of core component IDs")
            Map<String, List<String>> subModuleSpecs,
            ToolContext toolContext) {

        ModuleExecutionContext parentContext = ReadCodeComponentsTools.extractContext(toolContext);

        if (parentContext.hasReachedMaxDepth()) {
            log.warn("[{}] Max depth {} reached, sub-agent delegation skipped for {}",
                    parentContext.getModuleName(), parentContext.getMaxDepth(), subModuleSpecs.keySet());
            return "Max recursion depth reached. Sub-modules not generated: "
                    + String.join(", ", subModuleSpecs.keySet());
        }

        if (subModuleSpecs.isEmpty()) {
            return "No sub-modules specified.";
        }

        parentContext.getModuleTreeManager().registerSubModules(
                parentContext.getModulePath(), subModuleSpecs);

        List<CompletableFuture<SubAgentResult>> futures = new ArrayList<CompletableFuture<SubAgentResult>>();
        for (Map.Entry<String, List<String>> entry : subModuleSpecs.entrySet()) {
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
