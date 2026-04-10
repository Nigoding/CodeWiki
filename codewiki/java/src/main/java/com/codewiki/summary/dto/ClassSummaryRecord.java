package com.codewiki.summary.dto;

import com.codewiki.util.Texts;

import java.util.Collections;
import java.util.List;

public class ClassSummaryRecord {

    private final String componentId;
    private final String moduleName;
    private final String relativePath;
    private final String className;
    private final ClassSummary classSummary;
    private final String rawSummaryJson;

    public ClassSummaryRecord(String componentId,
                              String moduleName,
                              String relativePath,
                              String className,
                              ClassSummary classSummary,
                              String rawSummaryJson) {
        this.componentId = componentId;
        this.moduleName = moduleName;
        this.relativePath = relativePath;
        this.className = className;
        this.classSummary = classSummary;
        this.rawSummaryJson = rawSummaryJson;
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

    public ClassSummary getClassSummary() {
        return classSummary;
    }

    public String getRawSummaryJson() {
        return rawSummaryJson;
    }

    public String getPurpose() {
        return classSummary == null ? "" : Texts.trimToEmpty(classSummary.getPurpose());
    }

    public String getRole() {
        return classSummary == null ? "" : Texts.trimToEmpty(classSummary.getRole());
    }

    public String getKeyFunctionality() {
        return classSummary == null ? "" : Texts.trimToEmpty(classSummary.getKeyFunctionality());
    }

    public List<String> getResponsibilities() {
        if (classSummary == null || Texts.trimToEmpty(classSummary.getKeyFunctionality()).isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(Texts.trimToEmpty(classSummary.getKeyFunctionality()));
    }

    public List<String> getDependencies() {
        return Collections.emptyList();
    }
}
