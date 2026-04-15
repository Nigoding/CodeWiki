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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class DefaultModuleBriefBuilder implements ModuleBriefBuilder {

    private final ModuleSummaryContextLoader summaryContextLoader;

    public DefaultModuleBriefBuilder(ModuleSummaryContextLoader summaryContextLoader) {
        this.summaryContextLoader = summaryContextLoader;
    }

    @Override
    public ModuleBrief build(ModuleExecutionContext ctx) {
        ModuleSummaryContext summaryCtx = resolveSummaryContext(ctx);

        List<PackageSummaryRecord> packageRecords = summaryCtx.getPackageSummaries();
        List<ClassSummaryRecord> classRecords = summaryCtx.getClassSummaries();
        List<MethodSummaryRecord> methodRecords = summaryCtx.getMethodSummaries();

        boolean summaryBacked = !packageRecords.isEmpty() || !classRecords.isEmpty() || !methodRecords.isEmpty();
        List<PackageSummary> packageSummaries = extractPackageSummaries(packageRecords);

        return new ModuleBrief(
                ctx.getModuleName(),
                inferModulePurpose(classRecords, methodRecords),
                aggregateBusinessValue(packageSummaries),
                aggregateMainResponsibilities(classRecords, methodRecords),
                aggregateKeyComponents(ctx, classRecords),
                aggregateMajorDependencies(methodRecords),
                aggregateMajorSideEffects(methodRecords),
                aggregateOpenQuestions(summaryBacked, classRecords, methodRecords),
                summaryBacked
        );
    }

    private ModuleSummaryContext resolveSummaryContext(ModuleExecutionContext ctx) {
        ModuleSummaryContext summaryCtx = ctx.getSummaryContext();
        if (summaryCtx == null) {
            summaryCtx = summaryContextLoader.load(ctx);
        }
        return summaryCtx;
    }

    private String inferModulePurpose(List<ClassSummaryRecord> classRecords,
                                      List<MethodSummaryRecord> methodRecords) {
        List<String> hints = new ArrayList<String>();
        Set<String> seen = new LinkedHashSet<String>();
        for (ClassSummaryRecord record : classRecords) {
            addIfPresent(hints, seen, record.getPurpose(), 2);
            addIfPresent(hints, seen, record.getKeyFunctionality(), 2);
            if (hints.size() >= 2) {
                return String.join(" ", hints);
            }
        }
        for (MethodSummaryRecord record : methodRecords) {
            addIfPresent(hints, seen, record.getSummary(), 2);
            if (hints.size() >= 2) {
                break;
            }
        }
        return hints.isEmpty()
                ? "Module purpose should be inferred from the module tree and source evidence."
                : String.join(" ", hints);
    }

    private String aggregateBusinessValue(List<PackageSummary> packageSummaries) {
        List<String> values = new ArrayList<String>();
        Set<String> seen = new LinkedHashSet<String>();
        for (PackageSummary packageSummary : packageSummaries) {
            String value = packageSummary == null ? "" : Texts.trimToEmpty(packageSummary.getCoreBusinessFunction());
            if (!value.isEmpty() && seen.add(value)) {
                values.add(value);
            }
        }
        return values.isEmpty() ? "" : String.join(" | ", values);
    }

    private List<String> aggregateMainResponsibilities(List<ClassSummaryRecord> classRecords,
                                                       List<MethodSummaryRecord> methodRecords) {
        List<String> responsibilities = new ArrayList<String>();
        Set<String> seen = new LinkedHashSet<String>();
        for (ClassSummaryRecord record : classRecords) {
            addIfPresent(responsibilities, seen, record.getRole(), 4);
            addIfPresent(responsibilities, seen, record.getPurpose(), 4);
            addIfPresent(responsibilities, seen, record.getKeyFunctionality(), 4);
            if (responsibilities.size() >= 4) {
                return responsibilities;
            }
        }
        for (MethodSummaryRecord record : methodRecords) {
            addIfPresent(responsibilities, seen, record.getSummary(), 4);
            if (responsibilities.size() >= 4) {
                break;
            }
        }
        return responsibilities;
    }

    private List<String> aggregateKeyComponents(ModuleExecutionContext ctx, List<ClassSummaryRecord> classRecords) {
        List<String> names = new ArrayList<String>();
        Set<String> seen = new LinkedHashSet<String>();
        for (String componentId : ctx.getCoreComponentIds()) {
            Node node = ctx.getComponents().get(componentId);
            if (node == null) {
                continue;
            }
            String classFqn = Texts.trimToEmpty(node.getClassFqn());
            if (!classFqn.isEmpty()) {
                for (ClassSummaryRecord record : classRecords) {
                    if (classFqn.equals(record.getComponentId())) {
                        addIfPresent(names, seen, record.getClassName(), 6);
                        break;
                    }
                }
            } else {
                addIfPresent(names, seen, Texts.trimToEmpty(node.getName()), 6);
            }
        }
        return names;
    }

    private List<String> aggregateMajorDependencies(List<MethodSummaryRecord> methodRecords) {
        return Collections.emptyList();
    }

    private List<String> aggregateMajorSideEffects(List<MethodSummaryRecord> methodRecords) {
        List<String> effects = new ArrayList<String>();
        Set<String> seen = new LinkedHashSet<String>();
        for (MethodSummaryRecord record : methodRecords) {
            for (String effect : record.getSideEffects()) {
                addIfPresent(effects, seen, effect, 5);
                if (effects.size() >= 5) {
                    return effects;
                }
            }
        }
        return effects;
    }

    private List<String> aggregateOpenQuestions(boolean summaryBacked,
                                                List<ClassSummaryRecord> classRecords,
                                                List<MethodSummaryRecord> methodRecords) {
        List<String> questions = new ArrayList<String>();
        if (!summaryBacked) {
            questions.add("No structured summaries were found for this module. Inspect source when behavior details matter.");
            return questions;
        }
        if (classRecords.isEmpty()) {
            questions.add("Core component class summaries are missing; component responsibilities may need recall_summary or direct source inspection.");
        }
        if (methodRecords.isEmpty()) {
            questions.add("Representative method behavior is not covered by current summaries; use recall_summary before relying on source inspection.");
        }
        return questions;
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

    private void addIfPresent(List<String> target, Set<String> seen, String value, int maxItems) {
        String normalized = Texts.trimToEmpty(value);
        if (target.size() >= maxItems || normalized.isEmpty()) {
            return;
        }
        if (seen.add(normalized)) {
            target.add(normalized);
        }
    }

}
