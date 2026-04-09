package com.codewiki.summary.dto;

import java.util.Collections;
import java.util.List;

public class ClassSummaryRecord {

    private final String componentId;
    private final String moduleName;
    private final String relativePath;
    private final String className;
    private final String summary;
    private final List<String> responsibilities;
    private final List<String> dependencies;

    public ClassSummaryRecord(String componentId,
                              String moduleName,
                              String relativePath,
                              String className,
                              String summary,
                              List<String> responsibilities,
                              List<String> dependencies) {
        this.componentId = componentId;
        this.moduleName = moduleName;
        this.relativePath = relativePath;
        this.className = className;
        this.summary = summary;
        this.responsibilities = responsibilities == null ? Collections.<String>emptyList() : responsibilities;
        this.dependencies = dependencies == null ? Collections.<String>emptyList() : dependencies;
    }

    public String getComponentId() {
        return componentId;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getClassName() {
        return className;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getResponsibilities() {
        return responsibilities;
    }

    public List<String> getDependencies() {
        return dependencies;
    }
}
