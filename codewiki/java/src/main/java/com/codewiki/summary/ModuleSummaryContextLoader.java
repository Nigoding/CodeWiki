package com.codewiki.summary;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummaryRecord;
import com.codewiki.summary.dto.PackageSummaryRecord;
import com.codewiki.util.Texts;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads all relevant summaries for the given module execution context in a single batch.
 */
@Component
public class ModuleSummaryContextLoader {

    private final SummaryQueryService summaryQueryService;

    public ModuleSummaryContextLoader(SummaryQueryService summaryQueryService) {
        this.summaryQueryService = summaryQueryService;
    }

    public ModuleSummaryContext load(ModuleExecutionContext ctx) {
        String projectName = deriveProjectName(ctx);
        List<String> packageFqns = collectPackageFqns(ctx);
        List<String> classFqns = collectClassFqns(ctx);
        List<String> methodFqns = collectMethodFqns(ctx);

        List<PackageSummaryRecord> packages = summaryQueryService.findPackageSummaries(projectName, packageFqns);
        List<ClassSummaryRecord> classes = summaryQueryService.findClassSummaries(projectName, classFqns);
        List<MethodSummaryRecord> methods = methodFqns.isEmpty()
                ? summaryQueryService.findMethodSummaries(projectName, classFqns)
                : summaryQueryService.findMethodSummariesByFqns(projectName, methodFqns);

        return new ModuleSummaryContext(packages, classes, methods);
    }

    private List<String> collectClassFqns(ModuleExecutionContext ctx) {
        Set<String> values = new LinkedHashSet<String>();
        for (String componentId : ctx.getCoreComponentIds()) {
            Node node = ctx.getComponents().get(componentId);
            if (node != null && Texts.trimToEmpty(node.getClassFqn()).length() > 0) {
                values.add(Texts.trimToEmpty(node.getClassFqn()));
            }
        }
        return new ArrayList<String>(values);
    }

    private List<String> collectMethodFqns(ModuleExecutionContext ctx) {
        Set<String> values = new LinkedHashSet<String>();
        for (String componentId : ctx.getCoreComponentIds()) {
            Node node = ctx.getComponents().get(componentId);
            if (node != null && node.getMethodFqns() != null) {
                values.addAll(node.getMethodFqns());
            }
        }
        return new ArrayList<String>(values);
    }

    private List<String> collectPackageFqns(ModuleExecutionContext ctx) {
        Set<String> values = new LinkedHashSet<String>();
        for (String componentId : ctx.getCoreComponentIds()) {
            Node node = ctx.getComponents().get(componentId);
            if (node != null && node.getPackageFqns() != null) {
                values.addAll(node.getPackageFqns());
            }
        }
        return new ArrayList<String>(values);
    }

    private String deriveProjectName(ModuleExecutionContext ctx) {
        String repoPath = ctx.getAbsoluteRepoPath();
        if (repoPath == null || repoPath.isEmpty()) {
            return ctx.getModuleName();
        }
        int normalized = Math.max(repoPath.lastIndexOf('/'), repoPath.lastIndexOf('\\'));
        return normalized < 0 ? repoPath : repoPath.substring(normalized + 1);
    }
}
