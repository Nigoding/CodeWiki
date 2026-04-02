package com.codewiki.agent.strategy;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.evaluator.ModuleComplexityEvaluator;
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
 * Agent strategy for complex modules (multi-file, above token threshold, below max depth).
 *
 * Tool set: read_code_components + str_replace_editor + generate_sub_module_documentation
 * System prompt: system-complex.st
 *
 * This strategy is evaluated first (@Order(1)) because it carries the strictest preconditions.
 * If the module does NOT meet those conditions, the LeafModuleStrategy (@Order(2)) handles it.
 */
@Component
@Order(1)
public class ComplexModuleStrategy implements AgentStrategy {

    private static final Logger log = LoggerFactory.getLogger(ComplexModuleStrategy.class);

    private final ChatClient primaryChatClient;
    private final ChatClient fallbackChatClient;
    private final ReadCodeComponentsTools readTool;
    private final StrReplaceEditorTools editorTool;
    private final GenerateSubModuleDocTools subModuleTool;
    private final PromptBuilderService promptBuilder;
    private final ModuleComplexityEvaluator evaluator;

    public ComplexModuleStrategy(
            @Primary ChatClient primaryChatClient,
            @Qualifier("fallback") ChatClient fallbackChatClient,
            ReadCodeComponentsTools readTool,
            StrReplaceEditorTools editorTool,
            GenerateSubModuleDocTools subModuleTool,
            PromptBuilderService promptBuilder,
            ModuleComplexityEvaluator evaluator) {
        this.primaryChatClient  = primaryChatClient;
        this.fallbackChatClient = fallbackChatClient;
        this.readTool           = readTool;
        this.editorTool         = editorTool;
        this.subModuleTool      = subModuleTool;
        this.promptBuilder      = promptBuilder;
        this.evaluator          = evaluator;
    }

    @Override
    public boolean supports(ModuleExecutionContext ctx) {
        int tokenCount = promptBuilder.countCoreComponentTokens(ctx);
        return evaluator.shouldUseComplexAgent(ctx, tokenCount);
    }

    @Override
    public Map<String, Object> execute(ModuleExecutionContext ctx) {
        log.info("[ComplexAgent] Processing module: {}", ctx.getModuleName());
        return doExecute(primaryChatClient, ctx);
    }

    @Override
    public Map<String, Object> executeWithFallback(ModuleExecutionContext ctx) {
        log.warn("[ComplexAgent] Switching to fallback model for module: {}", ctx.getModuleName());
        return doExecute(fallbackChatClient, ctx);
    }

    private Map<String, Object> doExecute(ChatClient client, ModuleExecutionContext ctx) {
        client.prompt()
                .system(promptBuilder.buildComplexSystemPrompt(ctx))
                .user(promptBuilder.buildUserPrompt(ctx))
                // All three tools registered; Spring AI discovers @Tool methods via reflection
                .tools(readTool, editorTool, subModuleTool)
                // Runtime context passed through Spring AI's ToolContext mechanism
                .toolContext(Collections.singletonMap(
                        GenerateSubModuleDocTools.CTX_KEY, ctx))
                .call()
                .content();

        return ctx.getModuleTreeManager().getReadOnlySnapshot();
    }
}
