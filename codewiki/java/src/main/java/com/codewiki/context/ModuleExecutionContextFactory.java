package com.codewiki.context;

import com.codewiki.domain.ModuleTask;
import com.codewiki.tree.ModuleTreeManager;
import org.springframework.stereotype.Component;

@Component
public class ModuleExecutionContextFactory {

    public ModuleExecutionContext create(ModuleTask task, ModuleTreeManager moduleTreeManager) {
        return ModuleExecutionContext.builder()
                .moduleName(task.getModuleName())
                .components(task.getComponents())
                .coreComponentIds(task.getCoreComponentIds())
                .modulePath(task.getModulePath())
                .absoluteDocsPath(task.getDocsPath())
                .absoluteRepoPath(task.getRepoPath())
                .maxDepth(task.getMaxDepth())
                .currentDepth(task.getCurrentDepth())
                .customInstructions(task.getCustomInstructions())
                .moduleTreeManager(moduleTreeManager)
                .build();
    }
}
