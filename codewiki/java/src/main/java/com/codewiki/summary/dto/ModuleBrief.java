package com.codewiki.summary.dto;

import java.util.Collections;
import java.util.List;

public class ModuleBrief {

    private final String moduleName;
    private final String modulePurpose;
    private final String businessValue;
    private final List<String> mainResponsibilities;
    private final List<String> keyComponents;
    private final List<String> majorDependencies;
    private final List<String> majorSideEffects;
    private final List<String> openQuestions;
    private final boolean summaryBacked;

    public ModuleBrief(String moduleName,
                       String modulePurpose,
                       String businessValue,
                       List<String> mainResponsibilities,
                       List<String> keyComponents,
                       List<String> majorDependencies,
                       List<String> majorSideEffects,
                       List<String> openQuestions,
                       boolean summaryBacked) {
        this.moduleName = moduleName;
        this.modulePurpose = modulePurpose;
        this.businessValue = businessValue;
        this.mainResponsibilities = mainResponsibilities == null ? Collections.<String>emptyList() : mainResponsibilities;
        this.keyComponents = keyComponents == null ? Collections.<String>emptyList() : keyComponents;
        this.majorDependencies = majorDependencies == null ? Collections.<String>emptyList() : majorDependencies;
        this.majorSideEffects = majorSideEffects == null ? Collections.<String>emptyList() : majorSideEffects;
        this.openQuestions = openQuestions == null ? Collections.<String>emptyList() : openQuestions;
        this.summaryBacked = summaryBacked;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getModulePurpose() {
        return modulePurpose;
    }

    public String getBusinessValue() {
        return businessValue;
    }

    public List<String> getMainResponsibilities() {
        return mainResponsibilities;
    }

    public List<String> getKeyComponents() {
        return keyComponents;
    }

    public List<String> getMajorDependencies() {
        return majorDependencies;
    }

    public List<String> getMajorSideEffects() {
        return majorSideEffects;
    }

    public List<String> getOpenQuestions() {
        return openQuestions;
    }

    public boolean isSummaryBacked() {
        return summaryBacked;
    }
}
