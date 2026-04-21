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

import com.codewiki.domain.Node;

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
        if (start >= 0 && end > start) {
            return response.substring(start + "<GROUPED_COMPONENTS>".length(), end).trim();
        }
        return extractJsonFallback(response);
    }

    private String extractJsonFallback(String response) {
        int braceStart = response.indexOf('{');
        int braceEnd = response.lastIndexOf('}');
        if (braceStart < 0 || braceEnd <= braceStart) {
            return null;
        }
        return response.substring(braceStart, braceEnd + 1).trim();
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
                    log.warn("[{}] Skipping component '{}': unknown or duplicated",
                            ctx.getModuleName(), componentId);
                    continue;
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

        Set<String> missing = new LinkedHashSet<String>(allowed);
        missing.removeAll(assigned);
        if (!missing.isEmpty()) {
            log.info("[{}] Pre-cluster plan missing {} components, auto-assigning by file path",
                    ctx.getModuleName(), missing.size());
            for (String componentId : missing) {
                String bestModule = findBestModule(componentId, validated, ctx);
                validated.get(bestModule).add(componentId);
            }
        }

        return PreClusterPlan.of(validated);
    }

    // ── best-module matching ─────────────────────────────────────────────────

    private static final Set<String> LAYER_SEGMENTS = Set.of(
            "controller", "service", "repository", "mapper",
            "entity", "dto", "vo", "config", "impl", "domain", "model"
    );

    private String findBestModule(String componentId,
                                  Map<String, List<String>> modules,
                                  ModuleExecutionContext ctx) {
        String targetMavenModule = extractMavenModule(componentId, ctx);
        String targetDomain = extractBusinessDomain(componentId);

        String bestModule = null;
        int bestScore = -1;

        for (Map.Entry<String, List<String>> entry : modules.entrySet()) {
            for (String existing : entry.getValue()) {
                int score = 0;
                if (targetMavenModule != null) {
                    String existingMaven = extractMavenModule(existing, ctx);
                    if (targetMavenModule.equals(existingMaven)) {
                        score += 1000;
                    }
                }
                score += commonPrefixLength(targetDomain, extractBusinessDomain(existing));

                if (score > bestScore) {
                    bestScore = score;
                    bestModule = entry.getKey();
                }
            }
        }

        if (bestModule != null) {
            return bestModule;
        }

        return modules.entrySet().stream()
                .max(java.util.Comparator.comparingInt(e -> e.getValue().size()))
                .map(Map.Entry::getKey)
                .orElse(modules.keySet().iterator().next());
    }

    private String extractMavenModule(String componentId, ModuleExecutionContext ctx) {
        Node node = ctx.getComponents().get(componentId);
        if (node == null || node.getRelativePath() == null) {
            return null;
        }
        String path = node.getRelativePath().replace('\\', '/');
        int srcIdx = path.indexOf("/src/");
        if (srcIdx <= 0) {
            return null;
        }
        return path.substring(0, srcIdx);
    }

    private String extractBusinessDomain(String componentId) {
        String pkg = componentId.contains(".")
                ? componentId.substring(0, componentId.lastIndexOf('.'))
                : "";
        StringBuilder sb = new StringBuilder();
        for (String segment : pkg.split("\\.")) {
            if (!LAYER_SEGMENTS.contains(segment.toLowerCase())) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(segment);
            }
        }
        return sb.toString();
    }

    private int commonPrefixLength(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int last = 0;
        for (int i = 0; i < len; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                break;
            }
            if (a.charAt(i) == '.') {
                last = i + 1;
            }
        }
        if (len > 0 && len <= a.length() && len <= b.length()
                && a.substring(0, len).equals(b.substring(0, len))) {
            last = len;
        }
        return last;
    }
}
