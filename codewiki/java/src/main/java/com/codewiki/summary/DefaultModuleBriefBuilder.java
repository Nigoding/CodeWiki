package com.codewiki.summary;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummaryRecord;
import com.codewiki.summary.dto.ModuleBrief;
import com.codewiki.summary.dto.PackageSummary;
import com.codewiki.summary.dto.PackageSummaryRecord;
import com.codewiki.util.Texts;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class DefaultModuleBriefBuilder implements ModuleBriefBuilder {

    private final ModuleSummaryContextLoader summaryContextLoader;
    private final SummaryFormatter summaryFormatter;

    public DefaultModuleBriefBuilder(ModuleSummaryContextLoader summaryContextLoader,
                                     SummaryFormatter summaryFormatter) {
        this.summaryContextLoader = summaryContextLoader;
        this.summaryFormatter = summaryFormatter;
    }

    @Override
    public ModuleBrief build(ModuleExecutionContext ctx) {
        ModuleSummaryContext summaryCtx = ctx.getSummaryContext();
        if (summaryCtx == null) {
            summaryCtx = summaryContextLoader.load(ctx);
        }

        List<PackageSummaryRecord> packageRecords = summaryCtx.getPackageSummaries();
        List<ClassSummaryRecord> classRecords = summaryCtx.getClassSummaries();
        List<MethodSummaryRecord> methodRecords = summaryCtx.getMethodSummaries();

        boolean summaryBacked = !packageRecords.isEmpty() || !classRecords.isEmpty() || !methodRecords.isEmpty();
        List<PackageSummary> packageSummaries = extractPackageSummaries(packageRecords);

        List<String> keyClassSummaries = new ArrayList<String>();
        for (int i = 0; i < Math.min(5, classRecords.size()); i++) {
            ClassSummaryRecord record = classRecords.get(i);
            keyClassSummaries.add(summaryFormatter.formatClassSummary(record));
        }

        List<String> keyMethodSummaries = new ArrayList<String>();
        for (int i = 0; i < Math.min(8, methodRecords.size()); i++) {
            MethodSummaryRecord record = methodRecords.get(i);
            keyMethodSummaries.add(summaryFormatter.formatMethodSummary(record));
        }

        Set<String> dependencySet = new LinkedHashSet<String>();
        for (MethodSummaryRecord record : methodRecords) {
            dependencySet.addAll(record.getSideEffects());
        }

        List<String> openQuestions = new ArrayList<String>();
        if (!summaryBacked) {
            openQuestions.add("No structured summaries were found for this module. Inspect source when behavior details matter.");
        }

        return new ModuleBrief(
                ctx.getModuleName(),
                inferPurpose(packageSummaries, classRecords, methodRecords),
                aggregateCoreBusinessFunction(packageSummaries),
                aggregateBusinessFlows(packageSummaries),
                aggregateBusinessEntities(packageSummaries),
                keyClassSummaries,
                keyMethodSummaries,
                new ArrayList<String>(dependencySet),
                openQuestions,
                summaryBacked
        );
    }

    private String inferPurpose(List<PackageSummary> packageSummaries,
                                List<ClassSummaryRecord> classRecords,
                                List<MethodSummaryRecord> methodRecords) {
        String packagePurpose = aggregateCoreBusinessFunction(packageSummaries);
        if (packagePurpose.length() > 0) {
            return packagePurpose;
        }
        if (!classRecords.isEmpty() && Texts.trimToEmpty(classRecords.get(0).getPurpose()).length() > 0) {
            return Texts.trimToEmpty(classRecords.get(0).getPurpose());
        }
        if (!methodRecords.isEmpty() && Texts.trimToEmpty(methodRecords.get(0).getSummary()).length() > 0) {
            return Texts.trimToEmpty(methodRecords.get(0).getSummary());
        }
        return "Purpose should be inferred from the module tree and source evidence.";
    }

    private List<String> aggregateBusinessFlows(List<PackageSummary> packageSummaries) {
        List<String> flows = new ArrayList<String>();
        Set<String> seen = new LinkedHashSet<String>();
        for (PackageSummary packageSummary : packageSummaries) {
            if (packageSummary == null || packageSummary.getBusinessFlows() == null) {
                continue;
            }
            for (PackageSummary.BusinessFlow flow : packageSummary.getBusinessFlows()) {
                StringBuilder sb = new StringBuilder();
                sb.append(Texts.trimToEmpty(flow.getFlowName()));
                if (Texts.trimToEmpty(flow.getDescription()).length() > 0) {
                    sb.append(": ").append(Texts.trimToEmpty(flow.getDescription()));
                }
                if (flow.getSteps() != null && !flow.getSteps().isEmpty()) {
                    sb.append(" Steps: ").append(String.join(" -> ", flow.getSteps()));
                }
                String value = sb.toString();
                if (!value.isEmpty() && seen.add(value)) {
                    flows.add(value);
                }
            }
        }
        return flows;
    }

    private List<String> aggregateBusinessEntities(List<PackageSummary> packageSummaries) {
        Set<String> entities = new LinkedHashSet<String>();
        for (PackageSummary packageSummary : packageSummaries) {
            if (packageSummary != null && packageSummary.getKeyBusinessEntities() != null) {
                entities.addAll(packageSummary.getKeyBusinessEntities());
            }
        }
        return new ArrayList<String>(entities);
    }

    private String aggregateCoreBusinessFunction(List<PackageSummary> packageSummaries) {
        List<String> values = new ArrayList<String>();
        Set<String> seen = new LinkedHashSet<String>();
        for (PackageSummary packageSummary : packageSummaries) {
            String value = packageSummary == null ? "" : Texts.trimToEmpty(packageSummary.getCoreBusinessFunction());
            if (!value.isEmpty() && seen.add(value)) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return "";
        }
        return String.join(" | ", values);
    }

    private List<PackageSummary> extractPackageSummaries(List<PackageSummaryRecord> packageRecords) {
        List<PackageSummary> results = new ArrayList<PackageSummary>();
        for (PackageSummaryRecord record : packageRecords) {
            if (record != null && record.getPackageSummary() != null) {
                results.add(record.getPackageSummary());
            }
        }
        return results;
    }

}
