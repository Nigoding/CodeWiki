package com.codewiki.context;

import com.codewiki.domain.Node;
import com.codewiki.tree.ModuleTreeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable per-module execution context.
 */
public class ModuleExecutionContext {

    private final String moduleName;
    private final Map<String, Node> components;
    private final List<String> coreComponentIds;
    private final List<String> modulePath;
    private final String absoluteDocsPath;
    private final String absoluteRepoPath;
    private final int maxDepth;
    private final int currentDepth;
    private final String customInstructions;
    private final ModuleTreeManager moduleTreeManager;

    private ModuleExecutionContext(Builder builder) {
        this.moduleName = builder.moduleName;
        this.components = Collections.unmodifiableMap(builder.components);
        this.coreComponentIds = Collections.unmodifiableList(builder.coreComponentIds);
        this.modulePath = Collections.unmodifiableList(builder.modulePath);
        this.absoluteDocsPath = builder.absoluteDocsPath;
        this.absoluteRepoPath = builder.absoluteRepoPath;
        this.maxDepth = builder.maxDepth;
        this.currentDepth = builder.currentDepth;
        this.customInstructions = builder.customInstructions;
        this.moduleTreeManager = builder.moduleTreeManager;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ModuleExecutionContext forSubModule(String subModuleName,
                                               List<String> subComponentIds) {
        List<String> newPath = new ArrayList<String>(this.modulePath);
        newPath.add(subModuleName);

        return ModuleExecutionContext.builder()
                .moduleName(subModuleName)
                .components(this.components)
                .coreComponentIds(new ArrayList<String>(subComponentIds))
                .modulePath(newPath)
                .absoluteDocsPath(this.absoluteDocsPath)
                .absoluteRepoPath(this.absoluteRepoPath)
                .maxDepth(this.maxDepth)
                .currentDepth(this.currentDepth + 1)
                .customInstructions(this.customInstructions)
                .moduleTreeManager(this.moduleTreeManager)
                .build();
    }

    public boolean hasReachedMaxDepth() {
        return this.currentDepth >= this.maxDepth;
    }

    public String getModuleName() { return moduleName; }
    public Map<String, Node> getComponents() { return components; }
    public List<String> getCoreComponentIds() { return coreComponentIds; }
    public List<String> getModulePath() { return modulePath; }
    public String getAbsoluteDocsPath() { return absoluteDocsPath; }
    public String getAbsoluteRepoPath() { return absoluteRepoPath; }
    public int getMaxDepth() { return maxDepth; }
    public int getCurrentDepth() { return currentDepth; }
    public String getCustomInstructions() { return customInstructions; }
    public ModuleTreeManager getModuleTreeManager() { return moduleTreeManager; }

    public static final class Builder {
        private String moduleName;
        private Map<String, Node> components;
        private List<String> coreComponentIds;
        private List<String> modulePath = new ArrayList<String>();
        private String absoluteDocsPath;
        private String absoluteRepoPath;
        private int maxDepth = 3;
        private int currentDepth = 1;
        private String customInstructions;
        private ModuleTreeManager moduleTreeManager;

        private Builder() {
        }

        public Builder moduleName(String v) { this.moduleName = v; return this; }
        public Builder components(Map<String, Node> v) { this.components = v; return this; }
        public Builder coreComponentIds(List<String> v) { this.coreComponentIds = v; return this; }
        public Builder modulePath(List<String> v) { this.modulePath = v; return this; }
        public Builder absoluteDocsPath(String v) { this.absoluteDocsPath = v; return this; }
        public Builder absoluteRepoPath(String v) { this.absoluteRepoPath = v; return this; }
        public Builder maxDepth(int v) { this.maxDepth = v; return this; }
        public Builder currentDepth(int v) { this.currentDepth = v; return this; }
        public Builder customInstructions(String v) { this.customInstructions = v; return this; }
        public Builder moduleTreeManager(ModuleTreeManager v) { this.moduleTreeManager = v; return this; }

        public ModuleExecutionContext build() {
            if (moduleName == null) throw new IllegalStateException("moduleName is required");
            if (components == null) throw new IllegalStateException("components is required");
            if (coreComponentIds == null) throw new IllegalStateException("coreComponentIds is required");
            if (absoluteDocsPath == null) throw new IllegalStateException("absoluteDocsPath is required");
            if (absoluteRepoPath == null) throw new IllegalStateException("absoluteRepoPath is required");
            if (moduleTreeManager == null) throw new IllegalStateException("moduleTreeManager is required");
            return new ModuleExecutionContext(this);
        }
    }
}
