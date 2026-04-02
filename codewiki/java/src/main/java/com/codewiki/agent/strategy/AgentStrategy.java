package com.codewiki.agent.strategy;

import com.codewiki.context.ModuleExecutionContext;

import java.util.Map;

/**
 * Strategy interface for module documentation agents.
 *
 * Each implementation encapsulates one "agent type" (complex or leaf) including:
 *  - the complexity predicate (supports)
 *  - the tool set registered with ChatClient
 *  - the system prompt variant
 *  - the fallback model switch
 *
 * Spring collects all beans implementing this interface into a List<AgentStrategy>
 * in the orchestrator, ordered by @Order annotation, so the first matching strategy wins.
 */
public interface AgentStrategy {

    /**
     * Returns true if this strategy can handle the given module.
     * Strategies are evaluated in ascending @Order; the first match is used.
     */
    boolean supports(ModuleExecutionContext ctx);

    /**
     * Execute documentation generation using the primary model.
     *
     * @return the updated module tree snapshot after execution
     */
    Map<String, Object> execute(ModuleExecutionContext ctx);

    /**
     * Execute using the fallback model (called by the orchestrator after primary failure).
     * Implementations that do not support a distinct fallback may delegate to execute().
     */
    Map<String, Object> executeWithFallback(ModuleExecutionContext ctx);
}
