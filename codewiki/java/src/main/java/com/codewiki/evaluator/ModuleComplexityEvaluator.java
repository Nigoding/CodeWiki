package com.codewiki.evaluator;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Determines whether a module should use the complex-agent path.
 *
 * Python logic:
 *   is_complex_module(components, core_component_ids)
 * where a module is complex when its core components span more than one file.
 *
 * Java keeps one additional guard for strategy selection:
 *   current_depth < max_depth
 * so recursion remains bounded.
 */
@Component
public class ModuleComplexityEvaluator {

    /**
     * Structural complexity check copied from the Python implementation.
     * A module is complex if its core components belong to more than one file.
     */
    public boolean isComplex(Map<String, Node> components, List<String> coreComponentIds) {
        Set<String> files = new HashSet<String>();
        for (String id : coreComponentIds) {
            Node node = components.get(id);
            if (node != null) {
                files.add(node.getFilePath());
            }
        }
        return files.size() > 1;
    }

    /**
     * Full strategy-selection check.
     */
    public boolean shouldUseComplexAgent(ModuleExecutionContext ctx) {
        if (ctx.hasReachedMaxDepth()) {
            return false;
        }
        return isComplex(ctx.getComponents(), ctx.getCoreComponentIds());
    }
}
