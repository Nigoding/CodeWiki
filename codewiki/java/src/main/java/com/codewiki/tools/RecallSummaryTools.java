package com.codewiki.tools;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.summary.ModuleSummaryContext;
import com.codewiki.summary.ModuleSummaryContextLoader;
import com.codewiki.summary.SummaryFormatter;
import com.codewiki.summary.dto.MethodSummaryRecord;
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

    @Tool("Get method-level summaries for a specific core component when its class-level summary is not enough.")
    public String getMethodSummariesForComponent(
            @ToolParam(description = "Core component class fully qualified name, e.g. com.example.auth.AuthService")
            String componentId,
            ToolContext toolContext) {

        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        if (!isCoreComponentClass(ctx, componentId)) {
            return "Component is not part of the current module's core components: " + componentId;
        }

        ModuleSummaryContext summaryCtx = resolveContext(ctx);
        List<MethodSummaryRecord> records = summaryCtx.getMethodSummariesByClass(componentId);
        if (records.isEmpty()) {
            return "No method-level summaries found for core component: " + componentId;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Component: ").append(componentId).append("\n\n");
        sb.append("Methods:\n");
        for (MethodSummaryRecord record : records) {
            sb.append("- ").append(summaryFormatter.formatMethodSummaryRecall(record)).append("\n");
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

    private boolean isCoreComponentClass(ModuleExecutionContext ctx, String componentId) {
        Set<String> classFqns = new LinkedHashSet<String>();
        for (String coreComponentId : ctx.getCoreComponentIds()) {
            Node node = ctx.getComponents().get(coreComponentId);
            if (node == null) {
                continue;
            }
            String classFqn = Texts.trimToEmpty(node.getClassFqn());
            if (!classFqn.isEmpty()) {
                classFqns.add(classFqn);
            }
        }
        return classFqns.contains(Texts.trimToEmpty(componentId));
    }
}
