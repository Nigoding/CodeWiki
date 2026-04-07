package com.codewiki.domain;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ModuleTask {

    private final String moduleName;
    private final Map<String, Node> components;
    private final List<String> coreComponentIds;
    private final List<String> modulePath;
    private final String docsPath;
    private final String repoPath;
    private final int maxDepth;
    private final int currentDepth;
    private final String customInstructions;

    public ModuleTask(String moduleName,
                      Map<String, Node> components,
                      List<String> coreComponentIds,
                      List<String> modulePath,
                      String docsPath,
                      String repoPath,
                      int maxDepth,
                      int currentDepth,
                      String customInstructions) {
        this.moduleName = moduleName;
        this.components = Collections.unmodifiableMap(components);
        this.coreComponentIds = Collections.unmodifiableList(coreComponentIds);
        this.modulePath = Collections.unmodifiableList(modulePath);
        this.docsPath = docsPath;
        this.repoPath = repoPath;
        this.maxDepth = maxDepth;
        this.currentDepth = currentDepth;
        this.customInstructions = customInstructions;
    }

    public String getModuleName() {
        return moduleName;
    }

    public Map<String, Node> getComponents() {
        return components;
    }

    public List<String> getCoreComponentIds() {
        return coreComponentIds;
    }

    public List<String> getModulePath() {
        return modulePath;
    }

    public String getDocsPath() {
        return docsPath;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public int getCurrentDepth() {
        return currentDepth;
    }

    public String getCustomInstructions() {
        return customInstructions;
    }
}
