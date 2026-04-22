package com.codewiki.evaluator;

import com.codewiki.config.AgentProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.prompt.PromptBuilderService;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ModuleComplexityEvaluator {

    private final PromptBuilderService promptBuilderService;
    private final AgentProperties agentProperties;

    public ModuleComplexityEvaluator(PromptBuilderService promptBuilderService,
                                     AgentProperties agentProperties) {
        this.promptBuilderService = promptBuilderService;
        this.agentProperties = agentProperties;
    }

    public boolean isSpanningMultipleMavenModules(ModuleExecutionContext ctx) {
        List<String> mavenModules = ctx.getMavenModules();
        if (mavenModules == null || mavenModules.size() <= 1) {
            return false;
        }
        Map<String, Node> components = ctx.getComponents();
        Set<String> hit = new HashSet<String>();
        for (String id : ctx.getCoreComponentIds()) {
            Node node = components.get(id);
            if (node == null || node.getRelativePath() == null) {
                continue;
            }
            String path = node.getRelativePath().replace('\\', '/');
            String moduleName = matchMavenModule(path, mavenModules);
            if (moduleName != null) {
                hit.add(moduleName);
                if (hit.size() > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Decides whether the module should use the complex agent (with sub-module tool).
     *
     * Top-level (depth == 0): only structural complexity (Maven module span).
     * Sub-module recursion (depth > 0): Maven span + depth limit + token threshold.
     */
    public boolean shouldUseComplexAgent(ModuleExecutionContext ctx) {
        if (!isSpanningMultipleMavenModules(ctx)) {
            return false;
        }
        if (ctx.getCurrentDepth() == 0) {
            return true;
        }
        if (ctx.hasReachedMaxDepth()) {
            return false;
        }
        int tokens = promptBuilderService.countCoreComponentTokens(ctx);
        return tokens >= agentProperties.getMaxTokensPerLeafModule();
    }

    private String matchMavenModule(String normalizedPath, List<String> mavenModules) {
        String best = null;
        int bestLen = -1;
        for (String name : mavenModules) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (containsSegment(normalizedPath, name) && name.length() > bestLen) {
                best = name;
                bestLen = name.length();
            }
        }
        return best;
    }

    private boolean containsSegment(String path, String segment) {
        int idx = 0;
        while ((idx = path.indexOf(segment, idx)) >= 0) {
            boolean leftOk = idx == 0 || path.charAt(idx - 1) == '/';
            int end = idx + segment.length();
            boolean rightOk = end == path.length() || path.charAt(end) == '/';
            if (leftOk && rightOk) {
                return true;
            }
            idx = end;
        }
        return false;
    }
}
