package com.codewiki.summary.dto;

import java.util.Collections;
import java.util.List;

public class ModuleBrief {

    private final String moduleName;
    private final String purpose;
    private final String coreBusinessFunction;
    private final List<String> businessFlows;
    private final List<String> keyBusinessEntities;
    private final List<String> keyClassSummaries;
    private final List<String> keyMethodSummaries;
    private final List<String> dependencies;
    private final List<String> openQuestions;
    private final boolean summaryBacked;

    public ModuleBrief(String moduleName,
                       String purpose,
                       String coreBusinessFunction,
                       List<String> businessFlows,
                       List<String> keyBusinessEntities,
                       List<String> keyClassSummaries,
                       List<String> keyMethodSummaries,
                       List<String> dependencies,
                       List<String> openQuestions,
                       boolean summaryBacked) {
        this.moduleName = moduleName;
        this.purpose = purpose;
        this.coreBusinessFunction = coreBusinessFunction;
        this.businessFlows = businessFlows == null ? Collections.<String>emptyList() : businessFlows;
        this.keyBusinessEntities = keyBusinessEntities == null ? Collections.<String>emptyList() : keyBusinessEntities;
        this.keyClassSummaries = keyClassSummaries == null ? Collections.<String>emptyList() : keyClassSummaries;
        this.keyMethodSummaries = keyMethodSummaries == null ? Collections.<String>emptyList() : keyMethodSummaries;
        this.dependencies = dependencies == null ? Collections.<String>emptyList() : dependencies;
        this.openQuestions = openQuestions == null ? Collections.<String>emptyList() : openQuestions;
        this.summaryBacked = summaryBacked;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getCoreBusinessFunction() {
        return coreBusinessFunction;
    }

    public List<String> getBusinessFlows() {
        return businessFlows;
    }

    public List<String> getKeyBusinessEntities() {
        return keyBusinessEntities;
    }

    public List<String> getKeyClassSummaries() {
        return keyClassSummaries;
    }

    public List<String> getKeyMethodSummaries() {
        return keyMethodSummaries;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public List<String> getOpenQuestions() {
        return openQuestions;
    }

    public boolean isSummaryBacked() {
        return summaryBacked;
    }
}
