package com.codewiki.summary.dto;

import com.codewiki.util.Texts;

import java.util.Collections;
import java.util.List;

public class MethodSummaryRecord {

    private final String methodId;
    private final String componentId;
    private final String relativePath;
    private final String className;
    private final String methodName;
    private final MethodSummary methodSummary;
    private final String rawSummaryJson;

    public MethodSummaryRecord(String methodId,
                               String componentId,
                               String relativePath,
                               String className,
                               String methodName,
                               MethodSummary methodSummary,
                               String rawSummaryJson) {
        this.methodId = methodId;
        this.componentId = componentId;
        this.relativePath = relativePath;
        this.className = className;
        this.methodName = methodName;
        this.methodSummary = methodSummary;
        this.rawSummaryJson = rawSummaryJson;
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

    public MethodSummary getMethodSummary() {
        return methodSummary;
    }

    public String getRawSummaryJson() {
        return rawSummaryJson;
    }

    public String getSummary() {
        if (methodSummary == null) {
            return "";
        }
        String finalSummary = Texts.trimToEmpty(methodSummary.getFinalSummary());
        return finalSummary.isEmpty() ? Texts.trimToEmpty(methodSummary.getPurpose()) : finalSummary;
    }

    public String getPurpose() {
        return methodSummary == null ? "" : Texts.trimToEmpty(methodSummary.getPurpose());
    }

    public String getOutputs() {
        return methodSummary == null ? "" : Texts.trimToEmpty(methodSummary.getOutputs());
    }

    public String getDataFlow() {
        return methodSummary == null ? "" : Texts.trimToEmpty(methodSummary.getDataFlow());
    }

    public String getSequenceDiagram() {
        return methodSummary == null ? "" : Texts.trimToEmpty(methodSummary.getSequenceDiagram());
    }

    public List<String> getInputs() {
        return methodSummary == null || methodSummary.getInputs() == null
                ? Collections.<String>emptyList()
                : methodSummary.getInputs();
    }

    public List<String> getWorkflow() {
        return methodSummary == null || methodSummary.getWorkflow() == null
                ? Collections.<String>emptyList()
                : methodSummary.getWorkflow();
    }

    public List<String> getSideEffects() {
        return methodSummary == null || methodSummary.getSideEffects() == null
                ? Collections.<String>emptyList()
                : methodSummary.getSideEffects();
    }
}
