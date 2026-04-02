package com.codewiki.evaluator;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Determines whether a module warrants a "complex" agent (one that can spawn
 * sub-agents) versus a "leaf" agent that just writes a single documentation file.
 *
 * The logic mirrors the Python triple-condition:
 *   is_complex_module(components, core_ids)
 *   AND current_depth < max_depth
 *   AND num_tokens >= max_token_per_leaf_module
 *
 * Splitting this into an injectable Spring component means:
 *  - The heuristic can be unit-tested in isolation.
 *  - Alternative strategies (e.g. ML-based complexity scoring) can replace it
 *    without touching any strategy or orchestration code.
 */
@Component
public class ModuleComplexityEvaluator {

    /**
     * Token count above which a module's prompt is large enough to justify
     * spawning dedicated sub-agents.  Sourced from AgentProperties but bound
     * directly here via @Value to keep this class independent of that config class.
     */
    @Value("${codewiki.agent.max-tokens-per-leaf-module:4000}")
    private int maxTokensPerLeafModule;

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * Quick structural check: does the module span more than one source file?
     * Used during tree construction before a full token count is available.
     */
    public boolean isComplex(Map<String, Node> components, List<String> coreComponentIds) {
        Set<String> files = new HashSet<>();
        for (String id : coreComponentIds) {
            Node node = components.get(id);
            if (node != null) {
                files.add(node.getFilePath());
            }
        }
        return files.size() > 1;
    }

    /**
     * Full evaluation used by strategy selection.
     *
     * Returns {@code true} if ALL three conditions hold:
     *  1. The module spans more than one source file.
     *  2. The recursion depth limit has not been reached.
     *  3. The combined prompt token count exceeds the leaf threshold.
     *
     * @param ctx         current module execution context
     * @param tokenCount  pre-computed token count for this module's prompt content
     */
    public boolean shouldUseComplexAgent(ModuleExecutionContext ctx, int tokenCount) {
        if (ctx.hasReachedMaxDepth()) {
            return false;
        }
        if (!isComplex(ctx.getComponents(), ctx.getCoreComponentIds())) {
            return false;
        }
        return tokenCount >= maxTokensPerLeafModule;
    }

    // ── package-visible for testing ───────────────────────────────────────────

    void setMaxTokensPerLeafModule(int value) {
        this.maxTokensPerLeafModule = value;
    }
}
