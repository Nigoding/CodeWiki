package com.codewiki.summary;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummaryRecord;
import com.codewiki.summary.dto.ModuleBrief;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class DefaultModuleBriefBuilder implements ModuleBriefBuilder {

    private final SummaryQueryService summaryQueryService;

    public DefaultModuleBriefBuilder(SummaryQueryService summaryQueryService) {
        this.summaryQueryService = summaryQueryService;
    }

    @Override
    public ModuleBrief build(ModuleExecutionContext ctx) {
        List<ClassSummaryRecord> classRecords =
                summaryQueryService.findClassSummariesByComponentIds(ctx.getCoreComponentIds());
        List<MethodSummaryRecord> methodRecords =
                summaryQueryService.findMethodSummariesByComponentIds(ctx.getCoreComponentIds());

        boolean summaryBacked = !classRecords.isEmpty() || !methodRecords.isEmpty();

        List<String> keyClassSummaries = new ArrayList<String>();
        for (int i = 0; i < Math.min(5, classRecords.size()); i++) {
            ClassSummaryRecord record = classRecords.get(i);
            keyClassSummaries.add(record.getClassName() + " (" + record.getRelativePath() + "): " + safe(record.getSummary()));
        }

        List<String> keyMethodSummaries = new ArrayList<String>();
        for (int i = 0; i < Math.min(8, methodRecords.size()); i++) {
            MethodSummaryRecord record = methodRecords.get(i);
            keyMethodSummaries.add(record.getClassName() + "#" + record.getMethodName() + ": " + safe(record.getSummary()));
        }

        Set<String> dependencySet = new LinkedHashSet<String>();
        for (ClassSummaryRecord record : classRecords) {
            dependencySet.addAll(record.getDependencies());
        }

        List<String> openQuestions = new ArrayList<String>();
        if (!summaryBacked) {
            openQuestions.add("No structured summaries were found for this module. Inspect source when behavior details matter.");
        }

        return new ModuleBrief(
                ctx.getModuleName(),
                inferPurpose(classRecords, methodRecords),
                keyClassSummaries,
                keyMethodSummaries,
                new ArrayList<String>(dependencySet),
                openQuestions,
                summaryBacked
        );
    }

    private String inferPurpose(List<ClassSummaryRecord> classRecords, List<MethodSummaryRecord> methodRecords) {
        if (!classRecords.isEmpty() && safe(classRecords.get(0).getSummary()).length() > 0) {
            return safe(classRecords.get(0).getSummary());
        }
        if (!methodRecords.isEmpty() && safe(methodRecords.get(0).getSummary()).length() > 0) {
            return safe(methodRecords.get(0).getSummary());
        }
        return "Purpose should be inferred from the module tree and source evidence.";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
