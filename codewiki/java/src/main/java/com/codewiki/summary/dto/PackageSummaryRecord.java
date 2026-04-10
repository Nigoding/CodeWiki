package com.codewiki.summary.dto;

public class PackageSummaryRecord {

    private final String moduleName;
    private final String packageName;
    private final PackageSummary packageSummary;

    public PackageSummaryRecord(String moduleName, String packageName, PackageSummary packageSummary) {
        this.moduleName = moduleName;
        this.packageName = packageName;
        this.packageSummary = packageSummary;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getPackageName() {
        return packageName;
    }

    public PackageSummary getPackageSummary() {
        return packageSummary;
    }
}
