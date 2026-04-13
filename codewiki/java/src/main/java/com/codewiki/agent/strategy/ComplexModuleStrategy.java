package com.codewiki.agent.strategy;

import com.codewiki.agent.AgentExecutionResult;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.evaluator.ModuleComplexityEvaluator;
import com.codewiki.prompt.PromptBuilderService;
import com.codewiki.service.DocumentationPersistenceService;
import com.codewiki.summary.ModuleSummaryContext;
import com.codewiki.summary.ModuleSummaryContextLoader;
import com.codewiki.tools.GenerateSubModuleDocTools;
import com.codewiki.tools.ReadCodeComponentsTools;
import com.codewiki.tools.RecallSummaryTools;
import com.codewiki.tools.StrReplaceEditorTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@Order(1)
public class ComplexModuleStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(ComplexModuleStrategy.class);

    private final ChatClient primaryChatClient;
    private final ChatClient fallbackChatClient;
    private final ReadCodeComponentsTools readTool;
    private final RecallSummaryTools recallSummaryTools;
    private final StrReplaceEditorTools editorTool;
    private final GenerateSubModuleDocTools subModuleTool;
    private final PromptBuilderService promptBuilder;
    private final ModuleComplexityEvaluator evaluator;
    private final DocumentationPersistenceService persistenceService;
    private final ModuleSummaryContextLoader summaryContextLoader;

    public ComplexModuleStrategy(
            @Primary ChatClient primaryChatClient,
            @Qualifier("fallback") ChatClient fallbackChatClient,
            ReadCodeComponentsTools readTool,
            RecallSummaryTools recallSummaryTools,
            StrReplaceEditorTools editorTool,
            GenerateSubModuleDocTools subModuleTool,
            PromptBuilderService promptBuilder,
            ModuleComplexityEvaluator evaluator,
            DocumentationPersistenceService persistenceService,
            ModuleSummaryContextLoader summaryContextLoader) {
        this.primaryChatClient  = primaryChatClient;
        this.fallbackChatClient = fallbackChatClient;
        this.readTool           = readTool;
        this.recallSummaryTools = recallSummaryTools;
        this.editorTool         = editorTool;
        this.subModuleTool      = subModuleTool;
        this.promptBuilder      = promptBuilder;
        this.evaluator          = evaluator;
        this.persistenceService = persistenceService;
        this.summaryContextLoader = summaryContextLoader;
    }

    @Override
    public boolean supports(ModuleExecutionContext ctx) {
        int tokenCount = promptBuilder.countCoreComponentTokens(ctx);
        return evaluator.shouldUseComplexAgent(ctx, tokenCount);
    }

    @Override
    public AgentExecutionResult execute(ModuleExecutionContext ctx) {
        log.info("[ComplexAgent] Processing module: {}", ctx.getModuleName());
        return doExecute(primaryChatClient, enrichWithSummaryContext(ctx), false);
    }

    @Override
    public AgentExecutionResult executeWithFallback(ModuleExecutionContext ctx) {
        log.warn("[ComplexAgent] Switching to fallback model for module: {}", ctx.getModuleName());
        return doExecute(fallbackChatClient, enrichWithSummaryContext(ctx), true);
    }

    private ModuleExecutionContext enrichWithSummaryContext(ModuleExecutionContext ctx) {
        ModuleSummaryContext summaryCtx = summaryContextLoader.load(ctx);
        return ctx.withSummaryContext(summaryCtx);
    }

    private AgentExecutionResult doExecute(ChatClient client, ModuleExecutionContext ctx, boolean fallback) {
        String content = client.prompt()
                .system(promptBuilder.buildComplexSystemPrompt(ctx))
                .user(promptBuilder.buildUserPrompt(ctx))
                .tools(recallSummaryTools, readTool, editorTool, subModuleTool)
                .toolContext(Collections.<String, Object>singletonMap(
                        GenerateSubModuleDocTools.CTX_KEY, ctx))
                .call()
                .content();

        boolean written = persistenceService.moduleDocExists(ctx.getAbsoluteDocsPath(), ctx.getModuleName());
        return new AgentExecutionResult(content, written, fallback);
    }
}
