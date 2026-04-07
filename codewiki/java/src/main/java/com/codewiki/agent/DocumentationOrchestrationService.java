package com.codewiki.agent;

import com.codewiki.agent.strategy.AgentStrategy;
import com.codewiki.config.AgentProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.context.ModuleExecutionContextFactory;
import com.codewiki.domain.ModuleTask;
import com.codewiki.exception.DocumentationGenerationException;
import com.codewiki.repository.ModuleTreeRepository;
import com.codewiki.service.DocumentationPersistenceService;
import com.codewiki.tree.ModuleTreeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Service
@EnableConfigurationProperties(AgentProperties.class)
public class DocumentationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentationOrchestrationService.class);

    private final List<AgentStrategy> strategies;
    private final ModuleTreeRepository moduleTreeRepository;
    private final ModuleExecutionContextFactory contextFactory;
    private final DocumentationPersistenceService persistenceService;
    private final AgentProperties agentProperties;

    public DocumentationOrchestrationService(List<AgentStrategy> strategies,
                                             ModuleTreeRepository moduleTreeRepository,
                                             ModuleExecutionContextFactory contextFactory,
                                             DocumentationPersistenceService persistenceService,
                                             AgentProperties agentProperties) {
        this.strategies = strategies;
        this.moduleTreeRepository = moduleTreeRepository;
        this.contextFactory = contextFactory;
        this.persistenceService = persistenceService;
        this.agentProperties = agentProperties;
    }

    public Map<String, Object> processModule(ModuleTask task) {
        ModuleTreeManager treeManager = moduleTreeRepository.load(
                task.getDocsPath(),
                agentProperties.getModuleTreeFilename()
        );

        ModuleExecutionContext context = contextFactory.create(task, treeManager);
        if (isAlreadyProcessed(context)) {
            log.info("Already processed, skipping module: {}", task.getModuleName());
            return treeManager.getReadOnlySnapshot();
        }

        if (!treeManager.containsTopLevelModule(task.getModuleName())) {
            treeManager.registerTopLevelModule(task.getModuleName(), task.getCoreComponentIds());
        }
        return processModuleContext(context);
    }

    public Map<String, Object> processModuleContext(ModuleExecutionContext context) {
        log.info("Processing module: {} (depth={})", context.getModuleName(), context.getCurrentDepth());

        if (isAlreadyProcessed(context)) {
            log.info("Already processed, skipping module: {}", context.getModuleName());
            return context.getModuleTreeManager().getReadOnlySnapshot();
        }

        AgentStrategy strategy = selectStrategy(context);
        AgentExecutionResult result = executeWithFallback(strategy, context);
        ModuleTask task = new ModuleTask(
                context.getModuleName(),
                context.getComponents(),
                context.getCoreComponentIds(),
                context.getModulePath(),
                context.getAbsoluteDocsPath(),
                context.getAbsoluteRepoPath(),
                context.getMaxDepth(),
                context.getCurrentDepth(),
                context.getCustomInstructions()
        );

        persistenceService.persist(task, context, result, context.getModuleTreeManager());
        return context.getModuleTreeManager().getReadOnlySnapshot();
    }

    private AgentStrategy selectStrategy(ModuleExecutionContext context) {
        for (AgentStrategy strategy : strategies) {
            if (strategy.supports(context)) {
                return strategy;
            }
        }
        throw new DocumentationGenerationException(
                context.getModuleName(),
                new IllegalStateException("No AgentStrategy found for module: " + context.getModuleName())
        );
    }

    private AgentExecutionResult executeWithFallback(AgentStrategy strategy, ModuleExecutionContext context) {
        try {
            return strategy.execute(context);
        } catch (Exception primaryEx) {
            log.warn("[{}] Primary model failed ({}), switching to fallback model",
                    context.getModuleName(), primaryEx.getMessage());
            try {
                return strategy.executeWithFallback(context);
            } catch (Exception fallbackEx) {
                log.error("[{}] Fallback model also failed: {}",
                        context.getModuleName(), fallbackEx.getMessage(), fallbackEx);
                throw new DocumentationGenerationException(context.getModuleName(), fallbackEx);
            }
        }
    }

    private boolean isAlreadyProcessed(ModuleExecutionContext context) {
        return Files.exists(Paths.get(context.getAbsoluteDocsPath(), agentProperties.getOverviewFilename()))
                || persistenceService.moduleDocExists(context.getAbsoluteDocsPath(), context.getModuleName());
    }
}
