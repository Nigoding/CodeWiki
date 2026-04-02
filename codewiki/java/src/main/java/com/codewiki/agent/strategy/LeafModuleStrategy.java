package com.codewiki.agent.strategy;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.prompt.PromptBuilderService;
import com.codewiki.tools.GenerateSubModuleDocTools;
import com.codewiki.tools.ReadCodeComponentsTools;
import com.codewiki.tools.StrReplaceEditorTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Agent strategy for simple / leaf modules (single-file, or below the token threshold,
 * or at the maximum recursion depth).
 *
 * Tool set: read_code_components + str_replace_editor (NO generate_sub_module_documentation)
 * System prompt: system-leaf.st
 *
 * @Order(2) makes this the catch-all: supports() always returns true.
 * It is only reached when ComplexModuleStrategy.supports() returns false.
 */
@Component
@Order(2)
public class LeafModuleStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(LeafModuleStrategy.class);

    private final ChatClient primaryChatClient;
    private final ChatClient fallbackChatClient;
    private final ReadCodeComponentsTools readTool;
    private final StrReplaceEditorTools editorTool;
    private final PromptBuilderService promptBuilder;

    public LeafModuleStrategy(
            @Primary ChatClient primaryChatClient,
            @Qualifier("fallback") ChatClient fallbackChatClient,
            ReadCodeComponentsTools readTool,
            StrReplaceEditorTools editorTool,
            PromptBuilderService promptBuilder) {
        this.primaryChatClient  = primaryChatClient;
        this.fallbackChatClient = fallbackChatClient;
        this.readTool           = readTool;
        this.editorTool         = editorTool;
        this.promptBuilder      = promptBuilder;
    }

    /** Always returns true: this is the fallback strategy when no other matches. */
    @Override
    public boolean supports(ModuleExecutionContext ctx) {
        return true;
    }

    @Override
    public Map<String, Object> execute(ModuleExecutionContext ctx) {
        log.info("[LeafAgent] Processing module: {}", ctx.getModuleName());
        return doExecute(primaryChatClient, ctx);
    }

    @Override
    public Map<String, Object> executeWithFallback(ModuleExecutionContext ctx) {
        log.warn("[LeafAgent] Switching to fallback model for module: {}", ctx.getModuleName());
        return doExecute(fallbackChatClient, ctx);
    }

    private Map<String, Object> doExecute(ChatClient client, ModuleExecutionContext ctx) {
        client.prompt()
                .system(promptBuilder.buildLeafSystemPrompt(ctx))
                .user(promptBuilder.buildUserPrompt(ctx))
                // Only two tools: sub-module delegation is intentionally absent
                .tools(readTool, editorTool)
                .toolContext(Collections.singletonMap(
                        GenerateSubModuleDocTools.CTX_KEY, ctx))
                .call()
                .content();

        return ctx.getModuleTreeManager().getReadOnlySnapshot();
    }
}
