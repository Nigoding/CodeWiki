package com.codewiki.agent.strategy;

import com.codewiki.agent.AgentExecutionResult;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.prompt.PromptBuilderService;
import com.codewiki.service.DocumentationPersistenceService;
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
@Order(2)
public class LeafModuleStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(LeafModuleStrategy.class);

    private final ChatClient primaryChatClient;
    private final ChatClient fallbackChatClient;
    private final ReadCodeComponentsTools readTool;
    private final RecallSummaryTools recallSummaryTools;
    private final StrReplaceEditorTools editorTool;
    private final PromptBuilderService promptBuilder;
    private final DocumentationPersistenceService persistenceService;

    public LeafModuleStrategy(
            @Primary ChatClient primaryChatClient,
            @Qualifier("fallback") ChatClient fallbackChatClient,
            ReadCodeComponentsTools readTool,
            RecallSummaryTools recallSummaryTools,
            StrReplaceEditorTools editorTool,
            PromptBuilderService promptBuilder,
            DocumentationPersistenceService persistenceService) {
        this.primaryChatClient  = primaryChatClient;
        this.fallbackChatClient = fallbackChatClient;
        this.readTool           = readTool;
        this.recallSummaryTools = recallSummaryTools;
        this.editorTool         = editorTool;
        this.promptBuilder      = promptBuilder;
        this.persistenceService = persistenceService;
    }

    @Override
    public boolean supports(ModuleExecutionContext ctx) {
        return true;
    }

    @Override
    public AgentExecutionResult execute(ModuleExecutionContext ctx) {
        log.info("[LeafAgent] Processing module: {}", ctx.getModuleName());
        return doExecute(primaryChatClient, ctx, false);
    }

    @Override
    public AgentExecutionResult executeWithFallback(ModuleExecutionContext ctx) {
        log.warn("[LeafAgent] Switching to fallback model for module: {}", ctx.getModuleName());
        return doExecute(fallbackChatClient, ctx, true);
    }

    private AgentExecutionResult doExecute(ChatClient client, ModuleExecutionContext ctx, boolean fallback) {
        String content = client.prompt()
                .system(promptBuilder.buildLeafSystemPrompt(ctx))
                .user(promptBuilder.buildUserPrompt(ctx))
                .tools(recallSummaryTools, readTool, editorTool)
                .toolContext(Collections.<String, Object>singletonMap(
                        GenerateSubModuleDocTools.CTX_KEY, ctx))
                .call()
                .content();

        boolean written = persistenceService.moduleDocExists(ctx.getAbsoluteDocsPath(), ctx.getModuleName());
        return new AgentExecutionResult(content, written, fallback);
    }
}
