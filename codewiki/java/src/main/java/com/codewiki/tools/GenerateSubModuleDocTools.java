package com.codewiki.tools;

import com.codewiki.config.AgentConcurrencyProperties;
import com.codewiki.context.ModuleExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.ApplicationContext;
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

/**
 * Tool: generate_sub_module_documentation
 *
 * Delegates documentation of sub-modules to concurrent child agents.
 * Only available to agents running in "complex" mode (see ComplexModuleStrategy).
 *
 * Concurrency model
 * ────────────────────────────────────────────────────────────────────────────
 * 1. registerSubModules()  – single write-locked call to add all siblings to the
 *    module tree atomically before any child agent starts.
 *
 * 2. CompletableFuture per sub-module – all tasks are submitted to the shared
 *    sub-agent thread pool, then we wait for all of them before returning.
 *    This converts the Python sequential for-loop into true parallelism while
 *    keeping the parent agent blocked until every child is done (so the parent
 *    can still do cross-reference fixups afterwards).
 *
 * 3. Semaphore (llmRateLimitSemaphore) – each child task acquires a permit
 *    before making its LLM call(s) and releases it afterwards.  This is the
 *    fine-grained API-rate-limit control that the thread pool alone cannot
 *    provide (a thread may make multiple sequential LLM calls per agentic loop).
 *
 * 4. Partial failure tolerance – if some sub-agents fail the succeeded ones are
 *    retained and a summary of failures is returned to the parent LLM, allowing
 *    it to decide whether to retry or continue.
 *
 * Circular dependency avoidance
 * ────────────────────────────────────────────────────────────────────────────
 * This bean depends on DocumentationOrchestrationService, which in turn depends
 * on the strategy beans, which depend on this tool.  To break the cycle we use
 * ApplicationContext.getBean() lazily rather than constructor injection.
 */
@Component
public class GenerateSubModuleDocTools {

    private static final Logger log = LoggerFactory.getLogger(GenerateSubModuleDocTools.class);

    /** Context key used to pass ModuleExecutionContext through Spring AI's ToolContext */
    public static final String CTX_KEY = "executionContext";

    private final ExecutorService subAgentExecutor;
    private final Semaphore llmRateLimitSemaphore;
    private final AgentConcurrencyProperties concurrencyProps;
    private final ApplicationContext applicationContext;

    public GenerateSubModuleDocTools(
            ExecutorService subAgentExecutor,
            Semaphore llmRateLimitSemaphore,
            AgentConcurrencyProperties concurrencyProps,
            ApplicationContext applicationContext) {
        this.subAgentExecutor     = subAgentExecutor;
        this.llmRateLimitSemaphore = llmRateLimitSemaphore;
        this.concurrencyProps     = concurrencyProps;
        this.applicationContext   = applicationContext;
    }

    // ── Tool definition ───────────────────────────────────────────────────────

    @Tool("""
            Delegate documentation generation for a set of sub-modules to concurrent sub-agents.
            Each sub-module will be documented in its own {subModuleName}.md file.
            All sub-agents run in parallel; this call blocks until all have completed.

            subModuleSpecs: a JSON object mapping each sub-module name to the list of
            component IDs (from the current module's core components) that belong to it.
            Example:
            {
              "authentication": ["src/auth/handler.py::AuthHandler", "src/auth/middleware.py::verify_token"],
              "database":       ["src/db/client.py::DBClient", "src/db/models.py::UserModel"]
            }

            Returns a summary of which files were generated and any failures.
            """)
    public String generateSubModuleDocumentation(
            @ToolParam(description = "Map of sub-module name -> list of core component IDs")
            Map<String, List<String>> subModuleSpecs,
            ToolContext toolContext) {

        ModuleExecutionContext parentCtx = ReadCodeComponentsTools.extractContext(toolContext);

        // ── depth guard ──────────────────────────────────────────────────────
        if (parentCtx.hasReachedMaxDepth()) {
            log.warn("[{}] Max depth {} reached – sub-agent delegation skipped for: {}",
                    parentCtx.getModuleName(), parentCtx.getMaxDepth(),
                    subModuleSpecs.keySet());
            return "Max recursion depth reached. Sub-modules not generated: "
                    + String.join(", ", subModuleSpecs.keySet());
        }

        if (subModuleSpecs.isEmpty()) {
            return "No sub-modules specified.";
        }

        // ── Step 1: register all sub-modules atomically before any task starts ──
        parentCtx.getModuleTreeManager().registerSubModules(
                parentCtx.getModulePath(), subModuleSpecs);
        log.info("[{}] Registered {} sub-modules: {}",
                parentCtx.getModuleName(), subModuleSpecs.size(), subModuleSpecs.keySet());

        // ── Step 2: submit all sub-agent tasks concurrently ──────────────────
        List<CompletableFuture<SubAgentResult>> futures = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : subModuleSpecs.entrySet()) {
            String subModuleName      = entry.getKey();
            List<String> componentIds = entry.getValue();

            // Each sub-module gets its own immutable context snapshot – no shared state.
            ModuleExecutionContext subCtx = parentCtx.forSubModule(subModuleName, componentIds);

            String indent = indent(parentCtx.getCurrentDepth());
            log.info("{}└─ Queuing sub-module: {}", indent, subModuleName);

            CompletableFuture<SubAgentResult> future = CompletableFuture.supplyAsync(
                    () -> executeSubAgent(subModuleName, subCtx),
                    subAgentExecutor);
            futures.add(future);
        }

        // ── Step 3: wait for all tasks and collect results ───────────────────
        List<String> succeeded = new ArrayList<>();
        List<String> failed    = new ArrayList<>();

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

    // ── sub-agent execution unit ──────────────────────────────────────────────

    /**
     * Runs inside a thread from subAgentExecutor.
     * Acquires a rate-limit permit before invoking the orchestrator so that
     * the number of simultaneous LLM API calls stays within the configured limit
     * regardless of how many threads are active.
     */
    private SubAgentResult executeSubAgent(String subModuleName, ModuleExecutionContext subCtx) {
        boolean permitAcquired = false;
        try {
            String indent = indent(subCtx.getCurrentDepth());
            log.info("{}  → Awaiting rate-limit permit for: {}  [available permits: {}]",
                    indent, subModuleName, llmRateLimitSemaphore.availablePermits());

            // Block until a permit is available, with timeout to prevent indefinite hang
            permitAcquired = llmRateLimitSemaphore.tryAcquire(
                    concurrencyProps.getRateLimitTimeoutMinutes(), TimeUnit.MINUTES);

            if (!permitAcquired) {
                String msg = "Timed out waiting for LLM rate-limit permit after "
                        + concurrencyProps.getRateLimitTimeoutMinutes() + " minutes";
                log.warn("{}  ✗ {}: {}", indent, subModuleName, msg);
                return SubAgentResult.failure(subModuleName, msg);
            }

            log.info("{}  → Executing sub-agent: {}  [permits remaining: {}]",
                    indent, subModuleName, llmRateLimitSemaphore.availablePermits());

            // Lazy-resolve orchestrator to break the compile-time circular dependency
            // DocumentationOrchestrationService → strategies → this tool → orchestrator
            getOrchestrator().processModule(subCtx);

            log.info("{}  ✓ Sub-agent completed: {}", indent, subModuleName);
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

    // ── helpers ───────────────────────────────────────────────────────────────

    private com.codewiki.agent.DocumentationOrchestrationService getOrchestrator() {
        return applicationContext.getBean(
                com.codewiki.agent.DocumentationOrchestrationService.class);
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

    private static String indent(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("  ");
        return sb.toString();
    }

    // ── result value type ─────────────────────────────────────────────────────

    private static final class SubAgentResult {
        final String moduleName;
        final boolean success;
        final String errorMessage;

        private SubAgentResult(String moduleName, boolean success, String errorMessage) {
            this.moduleName   = moduleName;
            this.success      = success;
            this.errorMessage = errorMessage;
        }

        static SubAgentResult success(String name) {
            return new SubAgentResult(name, true, null);
        }

        static SubAgentResult failure(String name, String error) {
            return new SubAgentResult(name, false, error);
        }
    }
}
