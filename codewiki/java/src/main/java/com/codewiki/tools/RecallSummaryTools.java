package com.codewiki.tools;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.summary.SummaryQueryService;
import com.codewiki.summary.dto.PackageSummaryRecord;
import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummaryRecord;
import com.codewiki.util.Texts;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

@Component
public class RecallSummaryTools {

    private final SummaryQueryService summaryQueryService;

    public RecallSummaryTools(SummaryQueryService summaryQueryService) {
        this.summaryQueryService = summaryQueryService;
    }

    @Tool("Get structured class summaries for the current module.")
    public String getClassSummaries(ToolContext toolContext) {
        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        List<ClassSummaryRecord> records =
                summaryQueryService.findClassSummaries(deriveProjectName(ctx), collectClassFqns(ctx));

        if (records.isEmpty()) {
            return "No structured class summaries found for the current module.";
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(10, records.size());
        for (int i = 0; i < limit; i++) {
            ClassSummaryRecord record = records.get(i);
            sb.append("- ").append(record.getClassName())
                    .append(" [").append(record.getRelativePath()).append("]");
            if (Texts.trimToEmpty(record.getRole()).length() > 0) {
                sb.append(": role=").append(Texts.trimToEmpty(record.getRole()));
            }
            if (Texts.trimToEmpty(record.getKeyFunctionality()).length() > 0) {
                sb.append("; functionality=").append(Texts.trimToEmpty(record.getKeyFunctionality()));
            }
            if (Texts.trimToEmpty(record.getPurpose()).length() > 0) {
                sb.append("; purpose=").append(Texts.trimToEmpty(record.getPurpose()));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @Tool("Get package-level business summaries for the packages associated with the current module.")
    public String getPackageSummaries(ToolContext toolContext) {
        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        List<PackageSummaryRecord> records =
                summaryQueryService.findPackageSummaries(deriveProjectName(ctx), collectPackageFqns(ctx));

        if (records.isEmpty()) {
            return "No package summaries found for the current module.";
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(5, records.size());
        for (int i = 0; i < limit; i++) {
            PackageSummaryRecord record = records.get(i);
            if (record == null || record.getPackageSummary() == null) {
                continue;
            }
            sb.append("- Package ").append(record.getPackageName()).append(": ");
            if (Texts.trimToEmpty(record.getPackageSummary().getCoreBusinessFunction()).length() > 0) {
                sb.append(Texts.trimToEmpty(record.getPackageSummary().getCoreBusinessFunction()));
            }
            if (record.getPackageSummary().getKeyBusinessEntities() != null
                    && !record.getPackageSummary().getKeyBusinessEntities().isEmpty()) {
                sb.append(" Entities=")
                        .append(String.join(", ", record.getPackageSummary().getKeyBusinessEntities()));
            }
            sb.append("\n");
        }

        return sb.length() == 0 ? "No package summaries found for the current module." : sb.toString().trim();
    }

    @Tool("Get the package-level business summary for a specific package fully qualified name.")
    public String getPackageSummary(
            @ToolParam(description = "Package fully qualified name, e.g. com.example.order")
            String packageFqn,
            ToolContext toolContext) {
        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        if (!collectPackageFqns(ctx).contains(packageFqn)) {
            return "Package is not part of the current module context: " + packageFqn;
        }

        PackageSummaryRecord record = summaryQueryService.findPackageSummary(deriveProjectName(ctx), packageFqn);
        if (record == null || record.getPackageSummary() == null) {
            return "No package summary found for package: " + packageFqn;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("- Package: ").append(record.getPackageName()).append("\n");
        if (Texts.trimToEmpty(record.getPackageSummary().getCoreBusinessFunction()).length() > 0) {
            sb.append("- Core business function: ")
                    .append(Texts.trimToEmpty(record.getPackageSummary().getCoreBusinessFunction()))
                    .append("\n");
        }
        if (record.getPackageSummary().getKeyBusinessEntities() != null
                && !record.getPackageSummary().getKeyBusinessEntities().isEmpty()) {
            sb.append("- Key business entities: ")
                    .append(String.join(", ", record.getPackageSummary().getKeyBusinessEntities()))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    @Tool("Get structured method summaries for a component in the current module.")
    public String getMethodSummaries(
            @ToolParam(description = "Class fully qualified name, e.g. com.example.auth.AuthService")
            String componentId,
            ToolContext toolContext) {

        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        if (!collectClassFqns(ctx).contains(componentId)) {
            return "Class is not part of the current module context: " + componentId;
        }

        List<MethodSummaryRecord> records =
                summaryQueryService.findMethodSummariesByClass(deriveProjectName(ctx), componentId);

        if (records.isEmpty()) {
            return "No structured method summaries found for component: " + componentId;
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(12, records.size());
        for (int i = 0; i < limit; i++) {
            MethodSummaryRecord record = records.get(i);
            sb.append("- ").append(record.getClassName())
                    .append("#").append(record.getMethodName())
                    .append(": ").append(record.getSummary());
            if (!record.getInputs().isEmpty()) {
                sb.append(" Inputs=").append(String.join(", ", record.getInputs()));
            }
            if (Texts.trimToEmpty(record.getOutputs()).length() > 0) {
                sb.append(" Outputs=").append(Texts.trimToEmpty(record.getOutputs()));
            }
            if (!record.getSideEffects().isEmpty()) {
                sb.append(" SideEffects=").append(String.join(", ", record.getSideEffects()));
            }
            if (Texts.trimToEmpty(record.getDataFlow()).length() > 0) {
                sb.append(" DataFlow=").append(Texts.trimToEmpty(record.getDataFlow()));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private List<String> collectClassFqns(ModuleExecutionContext ctx) {
        Set<String> values = new LinkedHashSet<String>();
        for (String coreComponentId : ctx.getCoreComponentIds()) {
            Node node = ctx.getComponents().get(coreComponentId);
            if (node != null && Texts.trimToEmpty(node.getClassFqn()).length() > 0) {
                values.add(Texts.trimToEmpty(node.getClassFqn()));
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
