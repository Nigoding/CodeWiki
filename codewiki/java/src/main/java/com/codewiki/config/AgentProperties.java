package com.codewiki.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent behaviour configuration properties.
 * Bound from application.yml under prefix "codewiki.agent".
 */
@ConfigurationProperties(prefix = "codewiki.agent")
public class AgentProperties {

    /** Maximum recursive depth for sub-module agent delegation */
    private int maxDepth = 3;

    /**
     * Token threshold: a sub-module whose prompt exceeds this size is considered
     * worth delegating to its own agent.  Below this threshold the parent agent
     * documents it inline (leaf treatment).
     */
    private int maxTokensPerLeafModule = 4000;

    /** Whether to run LLM-based pre-clustering before documentation generation. */
    private boolean preClusterEnabled = true;

    /** Token threshold above which a module should be pre-clustered before documentation. */
    private int maxTokensPerClusterModule = 4000;

    /** File name of the persisted module-tree JSON file */
    private String moduleTreeFilename = "module_tree.json";

    /** File name of the top-level overview document */
    private String overviewFilename = "overview.md";

    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }

    public int getMaxTokensPerLeafModule() { return maxTokensPerLeafModule; }
    public void setMaxTokensPerLeafModule(int v) { this.maxTokensPerLeafModule = v; }

    public boolean isPreClusterEnabled() { return preClusterEnabled; }
    public void setPreClusterEnabled(boolean preClusterEnabled) { this.preClusterEnabled = preClusterEnabled; }

    public int getMaxTokensPerClusterModule() { return maxTokensPerClusterModule; }
    public void setMaxTokensPerClusterModule(int maxTokensPerClusterModule) {
        this.maxTokensPerClusterModule = maxTokensPerClusterModule;
    }

    public String getModuleTreeFilename() { return moduleTreeFilename; }
    public void setModuleTreeFilename(String v) { this.moduleTreeFilename = v; }

    public String getOverviewFilename() { return overviewFilename; }
    public void setOverviewFilename(String v) { this.overviewFilename = v; }
}
