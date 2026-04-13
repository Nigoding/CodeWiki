package com.codewiki.summary;

import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummaryRecord;
import com.codewiki.summary.dto.PackageSummaryRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable per-module request-level cache for loaded summaries.
 */
public class ModuleSummaryContext {

    private final Map<String, PackageSummaryRecord> packageMap;
    private final Map<String, ClassSummaryRecord> classMap;
    private final List<MethodSummaryRecord> methodList;
    private final Map<String, List<MethodSummaryRecord>> methodsByClass;

    public ModuleSummaryContext(List<PackageSummaryRecord> packageSummaries,
                                List<ClassSummaryRecord> classSummaries,
                                List<MethodSummaryRecord> methodSummaries) {
        Map<String, PackageSummaryRecord> pMap = new LinkedHashMap<String, PackageSummaryRecord>();
        if (packageSummaries != null) {
            for (PackageSummaryRecord r : packageSummaries) {
                if (r != null && r.getPackageName() != null) {
                    pMap.put(r.getPackageName(), r);
                }
            }
        }
        this.packageMap = Collections.unmodifiableMap(pMap);

        Map<String, ClassSummaryRecord> cMap = new LinkedHashMap<String, ClassSummaryRecord>();
        if (classSummaries != null) {
            for (ClassSummaryRecord r : classSummaries) {
                if (r != null && r.getComponentId() != null) {
                    cMap.put(r.getComponentId(), r);
                }
            }
        }
        this.classMap = Collections.unmodifiableMap(cMap);

        List<MethodSummaryRecord> mList = new ArrayList<MethodSummaryRecord>();
        if (methodSummaries != null) {
            mList.addAll(methodSummaries);
        }
        this.methodList = Collections.unmodifiableList(mList);

        Map<String, List<MethodSummaryRecord>> mbc = new LinkedHashMap<String, List<MethodSummaryRecord>>();
        for (MethodSummaryRecord r : mList) {
            if (r == null || r.getComponentId() == null) {
                continue;
            }
            mbc.computeIfAbsent(r.getComponentId(), k -> new ArrayList<MethodSummaryRecord>()).add(r);
        }
        for (Map.Entry<String, List<MethodSummaryRecord>> e : mbc.entrySet()) {
            e.setValue(Collections.unmodifiableList(e.getValue()));
        }
        this.methodsByClass = Collections.unmodifiableMap(mbc);
    }

    public PackageSummaryRecord getPackageSummary(String packageFqn) {
        return packageMap.get(packageFqn);
    }

    public List<PackageSummaryRecord> getPackageSummaries() {
        return new ArrayList<PackageSummaryRecord>(packageMap.values());
    }

    public ClassSummaryRecord getClassSummary(String classFqn) {
        return classMap.get(classFqn);
    }

    public List<ClassSummaryRecord> getClassSummaries() {
        return new ArrayList<ClassSummaryRecord>(classMap.values());
    }

    public List<MethodSummaryRecord> getMethodSummaries() {
        return methodList;
    }

    public List<MethodSummaryRecord> getMethodSummariesByClass(String classFqn) {
        List<MethodSummaryRecord> list = methodsByClass.get(classFqn);
        return list == null ? Collections.<MethodSummaryRecord>emptyList() : list;
    }

    public boolean isEmpty() {
        return packageMap.isEmpty() && classMap.isEmpty() && methodList.isEmpty();
    }

    public boolean hasPackageSummaries() {
        return !packageMap.isEmpty();
    }

    public boolean hasClassSummaries() {
        return !classMap.isEmpty();
    }

    public boolean hasMethodSummaries() {
        return !methodList.isEmpty();
    }
}
