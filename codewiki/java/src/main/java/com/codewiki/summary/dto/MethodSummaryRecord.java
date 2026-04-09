package com.codewiki.summary.dto;

import java.util.Collections;
import java.util.List;

public class MethodSummaryRecord {

    private final String methodId;
    private final String componentId;
    private final String relativePath;
    private final String className;
    private final String methodName;
    private final String summary;
    private final List<String> sideEffects;

    public MethodSummaryRecord(String methodId,
                               String componentId,
                               String relativePath,
                               String className,
                               String methodName,
                               String summary,
                               List<String> sideEffects) {
        this.methodId = methodId;
        this.componentId = componentId;
        this.relativePath = relativePath;
        this.className = className;
        this.methodName = methodName;
        this.summary = summary;
        this.sideEffects = sideEffects == null ? Collections.<String>emptyList() : sideEffects;
    }

    public String getMethodId() {
        return methodId;
    }

    public String getComponentId() {
        return componentId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getSideEffects() {
        return sideEffects;
    }
}
