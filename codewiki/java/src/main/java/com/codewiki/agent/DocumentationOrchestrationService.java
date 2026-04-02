package com.codewiki.agent;

import com.codewiki.agent.strategy.AgentStrategy;
import com.codewiki.config.AgentProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.exception.DocumentationGenerationException;
import com.codewiki.repository.ModuleTreeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Central orchestration service for module documentation generation.
 *
 * Responsibilities
 * ────────────────────────────────────────────────────────────────────────────
 * 1. Idempotency check – skip modules whose output file already exists.
 * 2. Strategy selection – iterate the @Order-sorted strategy list and pick
 *    the first one whose supports() predicate is satisfied.
 * 3. Primary execution – delegate to the chosen strategy.
 * 4. Fallback execution – if the primary call throws, retry with the fallback
 *    model via executeWithFallback().
 * 5. Persistence – save the updated module tree to disk after each successful run.
 *
 * This class is intentionally thin.  Complexity lives in the strategies
 * (agent type selection, tool registration) and in GenerateSubModuleDocTools
 * (concurrency, rate-limiting).  The orchestrator only coordinates them.
 *
 * Thread safety
 * ────────────────────────────────────────────────────────────────────────────
 * processModule() is called both from the main thread (top-level modules) and
 * from sub-agent worker threads (via GenerateSubModuleDocTools).  The method is
 * stateless; all mutable state lives in ModuleExecutionContext (immutable per
 * call) and ModuleTreeManager (internally locked).
 */
@Service
@EnableConfigurationProperties(AgentProperties.class)
public class DocumentationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentationOrchestrationService.class);

    /**
     * All AgentStrategy beans, automatically injected and sorted by @Order.
     * Spring builds this list from lowest to highest @Order value.
     */
    private final List<AgentStrategy> strategies;
    private final ModuleTreeRepository moduleTreeRepository;
    private final AgentProperties agentProperties;

    public DocumentationOrchestrationService(
            List<AgentStrategy> strategies,
            ModuleTreeRepository moduleTreeRepository,
            AgentProperties agentProperties) {
        this.strategies           = strategies;
        this.moduleTreeRepository = moduleTreeRepository;
        this.agentProperties      = agentProperties;
    }

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * Generate documentation for a single module.
     *
     * @param ctx  immutable execution context for this module
     * @return     the module tree snapshot after the agent has run
     * @throws DocumentationGenerationException if both primary and fallback models fail
     */
    public Map<String, Object> processModule(ModuleExecutionContext ctx) {
        log.info("Processing module: {} (depth={})", ctx.getModuleName(), ctx.getCurrentDepth());

        if (isAlreadyProcessed(ctx)) {
            log.info("Already processed, skipping: {}", ctx.getModuleName());
            return ctx.getModuleTreeManager().getReadOnlySnapshot();
        }

        AgentStrategy strategy = selectStrategy(ctx);
        log.debug("Selected strategy {} for module: {}",
                strategy.getClass().getSimpleName(), ctx.getModuleName());

        Map<String, Object> result = executeWithFallback(strategy, ctx);

        moduleTreeRepository.save(
                ctx.getAbsoluteDocsPath(),
                agentProperties.getModuleTreeFilename(),
                ctx.getModuleTreeManager());

        return result;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private AgentStrategy selectStrategy(ModuleExecutionContext ctx) {
        return strategies.stream()
                .filter(s -> s.supports(ctx))
                .findFirst()
                .orElseThrow(() -> new DocumentationGenerationException(
                        ctx.getModuleName(),
                        new IllegalStateException("No AgentStrategy found. " +
                                "Ensure at least one strategy bean is registered.")));
    }

    private Map<String, Object> executeWithFallback(AgentStrategy strategy,
                                                     ModuleExecutionContext ctx) {
        try {
            return strategy.execute(ctx);
        } catch (Exception primaryEx) {
            log.warn("[{}] Primary model failed ({}), switching to fallback model",
                    ctx.getModuleName(), primaryEx.getMessage());
            try {
                return strategy.executeWithFallback(ctx);
            } catch (Exception fallbackEx) {
                log.error("[{}] Fallback model also failed: {}",
                        ctx.getModuleName(), fallbackEx.getMessage(), fallbackEx);
                throw new DocumentationGenerationException(ctx.getModuleName(), fallbackEx);
            }
        }
    }

    /**
     * Idempotency: a module is considered processed if either its own .md file
     * or the top-level overview already exists in the output directory.
     * Matches the Python check in process_module().
     */
    private boolean isAlreadyProcessed(ModuleExecutionContext ctx) {
        java.nio.file.Path overviewPath = java.nio.file.Paths.get(
                ctx.getAbsoluteDocsPath(), agentProperties.getOverviewFilename());
        java.nio.file.Path docPath = java.nio.file.Paths.get(
                ctx.getAbsoluteDocsPath(), ctx.getModuleName() + ".md");
        return java.nio.file.Files.exists(overviewPath)
                || java.nio.file.Files.exists(docPath);
    }
}
