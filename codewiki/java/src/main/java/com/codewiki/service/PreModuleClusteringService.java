package com.codewiki.service;

import com.codewiki.config.AgentProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.prompt.PromptBuilderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PreModuleClusteringService {

    private static final Logger log = LoggerFactory.getLogger(PreModuleClusteringService.class);

    private final ChatClient primaryChatClient;
    private final ChatClient fallbackChatClient;
    private final PromptBuilderService promptBuilderService;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;

    public PreModuleClusteringService(
            @Primary ChatClient primaryChatClient,
            @Qualifier("fallback") ChatClient fallbackChatClient,
            PromptBuilderService promptBuilderService,
            AgentProperties agentProperties,
            ObjectMapper objectMapper) {
        this.primaryChatClient = primaryChatClient;
        this.fallbackChatClient = fallbackChatClient;
        this.promptBuilderService = promptBuilderService;
        this.agentProperties = agentProperties;
        this.objectMapper = objectMapper;
    }

    public PreClusterPlan cluster(ModuleExecutionContext ctx) {
        if (!shouldCluster(ctx)) {
            return PreClusterPlan.empty();
        }

        String prompt = promptBuilderService.buildClusterPrompt(ctx);
        PreClusterPlan primaryPlan = tryCluster(prompt, ctx, primaryChatClient, false);
        if (!primaryPlan.isEmpty()) {
            return primaryPlan;
        }
        return tryCluster(prompt, ctx, fallbackChatClient, true);
    }

    private boolean shouldCluster(ModuleExecutionContext ctx) {
        if (!agentProperties.isPreClusterEnabled()) {
            return false;
        }
        if (ctx.hasReachedMaxDepth()) {
            return false;
        }
        int clusterInputTokens = promptBuilderService.countClusterInputTokens(ctx);
        return clusterInputTokens > agentProperties.getMaxTokensPerClusterModule();
    }

    private PreClusterPlan tryCluster(String prompt,
                                      ModuleExecutionContext ctx,
                                      ChatClient chatClient,
                                      boolean fallback) {
        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return parsePlan(response, ctx, fallback);
        } catch (Exception e) {
            log.warn("[{}] Pre-clustering failed on {} model: {}",
                    ctx.getModuleName(), fallback ? "fallback" : "primary", e.getMessage());
            return PreClusterPlan.empty();
        }
    }

    private PreClusterPlan parsePlan(String response, ModuleExecutionContext ctx, boolean fallback) {
        String normalized = extractGroupedComponents(response);
        if (normalized == null) {
            log.warn("[{}] Pre-clustering {} response missing GROUPED_COMPONENTS tags",
                    ctx.getModuleName(), fallback ? "fallback" : "primary");
            return PreClusterPlan.empty();
        }

        try {
            Map<String, List<String>> raw = objectMapper.readValue(
                    normalized,
                    new TypeReference<LinkedHashMap<String, List<String>>>() {});
            return validatePlan(raw, ctx);
        } catch (Exception e) {
            log.warn("[{}] Failed to parse pre-clustering response: {}",
                    ctx.getModuleName(), e.getMessage());
            return PreClusterPlan.empty();
        }
    }

    private String extractGroupedComponents(String response) {
        if (response == null) {
            return null;
        }
        int start = response.indexOf("<GROUPED_COMPONENTS>");
        int end = response.indexOf("</GROUPED_COMPONENTS>");
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        return response.substring(start + "<GROUPED_COMPONENTS>".length(), end).trim();
    }

    private PreClusterPlan validatePlan(Map<String, List<String>> raw, ModuleExecutionContext ctx) {
        if (raw == null || raw.size() <= 1) {
            return PreClusterPlan.empty();
        }

        Set<String> allowed = new LinkedHashSet<String>(ctx.getCoreComponentIds());
        Set<String> assigned = new LinkedHashSet<String>();
        Map<String, List<String>> validated = new LinkedHashMap<String, List<String>>();

        for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
            String moduleName = entry.getKey() == null ? "" : entry.getKey().trim();
            if (moduleName.isEmpty()) {
                continue;
            }

            List<String> ids = entry.getValue();
            if (ids == null || ids.isEmpty()) {
                continue;
            }

            List<String> cleaned = new ArrayList<String>();
            for (String componentId : ids) {
                if (!allowed.contains(componentId) || assigned.contains(componentId)) {
                    log.warn("[{}] Rejecting invalid pre-cluster plan because component '{}' is unknown or duplicated",
                            ctx.getModuleName(), componentId);
                    return PreClusterPlan.empty();
                }
                cleaned.add(componentId);
                assigned.add(componentId);
            }

            if (!cleaned.isEmpty()) {
                validated.put(moduleName, cleaned);
            }
        }

        if (validated.size() <= 1) {
            return PreClusterPlan.empty();
        }

        if (assigned.size() != allowed.size()) {
            log.warn("[{}] Rejecting pre-cluster plan because it does not cover all core components exactly once",
                    ctx.getModuleName());
            return PreClusterPlan.empty();
        }

        return PreClusterPlan.of(validated);
    }
}
