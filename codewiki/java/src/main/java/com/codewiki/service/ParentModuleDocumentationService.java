package com.codewiki.service;

import com.codewiki.config.AgentProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.prompt.PromptBuilderService;
import com.codewiki.tree.ModuleTreeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParentModuleDocumentationService {

    private static final Logger log = LoggerFactory.getLogger(ParentModuleDocumentationService.class);

    private final ChatClient primaryChatClient;
    private final ChatClient fallbackChatClient;
    private final PromptBuilderService promptBuilderService;
    private final DocumentationPersistenceService persistenceService;
    private final AgentProperties agentProperties;

    public ParentModuleDocumentationService(
            @Primary ChatClient primaryChatClient,
            @Qualifier("fallback") ChatClient fallbackChatClient,
            PromptBuilderService promptBuilderService,
            DocumentationPersistenceService persistenceService,
            AgentProperties agentProperties) {
        this.primaryChatClient = primaryChatClient;
        this.fallbackChatClient = fallbackChatClient;
        this.promptBuilderService = promptBuilderService;
        this.persistenceService = persistenceService;
        this.agentProperties = agentProperties;
    }

    public void generate(ModuleExecutionContext ctx, List<String> childModuleNames, ModuleTreeManager treeManager) {
        if (persistenceService.moduleDocExists(ctx.getAbsoluteDocsPath(), ctx.getModuleName())) {
            return;
        }

        String prompt = promptBuilderService.buildParentOverviewPrompt(ctx, childModuleNames);
        String content = call(prompt, primaryChatClient);
        if (content == null) {
            content = call(prompt, fallbackChatClient);
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalStateException("Failed to generate parent overview for module: " + ctx.getModuleName());
        }

        persistenceService.writeModuleDoc(ctx.getAbsoluteDocsPath(), ctx.getModuleName(), content);
        treeManager.saveToFile(ctx.getAbsoluteDocsPath(), agentProperties.getModuleTreeFilename());
    }

    private String call(String prompt, ChatClient chatClient) {
        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return extractOverview(response);
        } catch (Exception e) {
            log.warn("Parent overview generation failed: {}", e.getMessage());
            return null;
        }
    }

    private String extractOverview(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        int start = response.indexOf("<OVERVIEW>");
        int end = response.indexOf("</OVERVIEW>");
        if (start >= 0 && end > start) {
            return response.substring(start + "<OVERVIEW>".length(), end).trim();
        }
        return response.trim();
    }
}
