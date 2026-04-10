package com.codewiki.summary;

import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummaryRecord;
import com.codewiki.summary.dto.PackageSummaryRecord;

import java.util.List;

public interface SummaryQueryService {

    ClassSummaryRecord findClassSummary(String projectName, String componentId);

    List<ClassSummaryRecord> findClassSummaries(String projectName, List<String> componentIds);

    MethodSummaryRecord findMethodSummaryByFqn(String projectName, String methodFqn);

    List<MethodSummaryRecord> findMethodSummariesByFqns(String projectName, List<String> methodFqns);

    List<MethodSummaryRecord> findMethodSummariesByClass(String projectName, String componentId);

    List<MethodSummaryRecord> findMethodSummaries(String projectName, List<String> componentIds);

    MethodSummaryRecord findMethodSummary(String projectName, String componentId, String methodName, String methodSignature);

    PackageSummaryRecord findPackageSummary(String projectName, String packageFqn);

    List<PackageSummaryRecord> findPackageSummaries(String projectName, List<String> packageFqns);

    PackageSummaryRecord findPackageSummaryByClass(String projectName, String componentId);
}
