package com.codewiki.tools;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.summary.SummaryQueryService;
import com.codewiki.summary.dto.ClassSummaryRecord;
import com.codewiki.summary.dto.MethodSummaryRecord;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

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
                summaryQueryService.findClassSummariesByComponentIds(ctx.getCoreComponentIds());

        if (records.isEmpty()) {
            return "No structured class summaries found for the current module.";
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(10, records.size());
        for (int i = 0; i < limit; i++) {
            ClassSummaryRecord record = records.get(i);
            sb.append("- ").append(record.getClassName())
                    .append(" [").append(record.getRelativePath()).append("]: ")
                    .append(record.getSummary())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    @Tool("Get structured method summaries for a component in the current module.")
    public String getMethodSummaries(
            @ToolParam(description = "Component ID, e.g. src/auth/AuthService.java::AuthService")
            String componentId,
            ToolContext toolContext) {

        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        if (!ctx.getCoreComponentIds().contains(componentId)) {
            return "Component is not part of the current module: " + componentId;
        }

        List<MethodSummaryRecord> records =
                summaryQueryService.findMethodSummariesByComponentIds(Collections.singletonList(componentId));

        if (records.isEmpty()) {
            return "No structured method summaries found for component: " + componentId;
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(12, records.size());
        for (int i = 0; i < limit; i++) {
            MethodSummaryRecord record = records.get(i);
            sb.append("- ").append(record.getClassName())
                    .append("#").append(record.getMethodName())
                    .append(": ").append(record.getSummary())
                    .append("\n");
        }
        return sb.toString().trim();
    }
}
