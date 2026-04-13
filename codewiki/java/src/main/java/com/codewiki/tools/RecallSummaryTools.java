package com.codewiki.tools;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.summary.ModuleSummaryContext;
import com.codewiki.summary.ModuleSummaryContextLoader;
import com.codewiki.summary.SummaryFormatter;
import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummaryRecord;
import com.codewiki.summary.dto.PackageSummaryRecord;
import com.codewiki.util.Texts;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RecallSummaryTools {

    private final ModuleSummaryContextLoader summaryContextLoader;
    private final SummaryFormatter summaryFormatter;

    public RecallSummaryTools(ModuleSummaryContextLoader summaryContextLoader,
                              SummaryFormatter summaryFormatter) {
        this.summaryContextLoader = summaryContextLoader;
        this.summaryFormatter = summaryFormatter;
    }

    @Tool("Get structured class summaries for the current module.")
    public String getClassSummaries(ToolContext toolContext) {
        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        ModuleSummaryContext summaryCtx = resolveContext(ctx);
        String formatted = summaryFormatter.formatClassSummaries(summaryCtx.getClassSummaries(), 10);
        return formatted.isEmpty()
                ? "No structured class summaries found for the current module."
                : formatted;
    }

    @Tool("Get package-level business summaries for the packages associated with the current module.")
    public String getPackageSummaries(ToolContext toolContext) {
        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        ModuleSummaryContext summaryCtx = resolveContext(ctx);
        String formatted = summaryFormatter.formatPackageSummaries(summaryCtx.getPackageSummaries(), 5);
        return formatted.isEmpty()
                ? "No package summaries found for the current module."
                : formatted;
    }

    @Tool("Get the package-level business summary for a specific package fully qualified name.")
    public String getPackageSummary(
            @ToolParam(description = "Package fully qualified name, e.g. com.example.order")
            String packageFqn,
            ToolContext toolContext) {
        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        ModuleSummaryContext summaryCtx = resolveContext(ctx);
        PackageSummaryRecord record = summaryCtx.getPackageSummary(packageFqn);
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

        ModuleSummaryContext summaryCtx = resolveContext(ctx);
        List<MethodSummaryRecord> records = summaryCtx.getMethodSummariesByClass(componentId);
        if (records.isEmpty()) {
            return "No structured method summaries found for component: " + componentId;
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(12, records.size());
        for (int i = 0; i < limit; i++) {
            sb.append("- ").append(summaryFormatter.formatMethodSummary(records.get(i))).append("\n");
        }
        return sb.toString().trim();
    }

    private ModuleSummaryContext resolveContext(ModuleExecutionContext ctx) {
        ModuleSummaryContext summaryCtx = ctx.getSummaryContext();
        if (summaryCtx == null) {
            summaryCtx = summaryContextLoader.load(ctx);
        }
        return summaryCtx;
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
}
