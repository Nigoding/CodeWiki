package com.codewiki.tools;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import com.codewiki.summary.ModuleSummaryContext;
import com.codewiki.summary.ModuleSummaryContextLoader;
import com.codewiki.summary.SummaryElementNames;
import com.codewiki.summary.SummaryQueryService;
import com.codewiki.summary.SummaryFormatter;
import com.codewiki.summary.dto.MethodSummaryRecord;
import com.codewiki.util.Texts;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RecallSummaryTools {

    private final ModuleSummaryContextLoader summaryContextLoader;
    private final SummaryQueryService summaryQueryService;
    private final SummaryFormatter summaryFormatter;

    public RecallSummaryTools(ModuleSummaryContextLoader summaryContextLoader,
                              SummaryQueryService summaryQueryService,
                              SummaryFormatter summaryFormatter) {
        this.summaryContextLoader = summaryContextLoader;
        this.summaryQueryService = summaryQueryService;
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

    @Tool("Get the method-level summary for one specific core-component method by using its display signature, e.g. createOrder(java.lang.String).")
    public String getMethodSummaryBySignature(
            @ToolParam(description = "Core component class fully qualified name, e.g. com.example.auth.AuthService")
            String componentId,
            @ToolParam(description = "Method display signature without the class FQN, e.g. login(java.lang.String,java.lang.String)")
            String methodSignature,
            ToolContext toolContext) {

        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        if (!isCoreComponentClass(ctx, componentId)) {
            return "Component is not part of the current module's core components: " + componentId;
        }

        String normalizedSignature = Texts.trimToEmpty(methodSignature);
        if (normalizedSignature.isEmpty()) {
            return "Method signature must not be empty.";
        }

        MethodSummaryRecord record = resolveMethodBySignature(ctx, componentId, normalizedSignature);
        if (record == null) {
            return "No method-level summary found for " + componentId + "#" + normalizedSignature;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Component: ").append(componentId).append("\n");
        sb.append("Method: ").append(summaryFormatter.formatMethodDisplaySignature(record)).append("\n\n");
        sb.append(summaryFormatter.formatMethodSummaryRecall(record));
        return sb.toString().trim();
    }

    private ModuleSummaryContext resolveContext(ModuleExecutionContext ctx) {
        ModuleSummaryContext summaryCtx = ctx.getSummaryContext();
        if (summaryCtx == null) {
            summaryCtx = summaryContextLoader.load(ctx);
        }
        return summaryCtx;
    }

    private MethodSummaryRecord resolveMethodBySignature(ModuleExecutionContext ctx,
                                                         String componentId,
                                                         String methodSignature) {
        ModuleSummaryContext summaryCtx = resolveContext(ctx);
        for (MethodSummaryRecord record : summaryCtx.getMethodSummariesByClass(componentId)) {
            String displaySignature = summaryFormatter.formatMethodDisplaySignature(record);
            if (methodSignature.equals(displaySignature)) {
                return record;
            }
        }

        String methodName = SummaryElementNames.extractMethodName(componentId + "#" + methodSignature);
        String rawSignature = SummaryElementNames.extractMethodSignature(componentId + "#" + methodSignature);
        if (methodName.isEmpty()) {
            return null;
        }
        return summaryQueryService.findMethodSummary(deriveProjectName(ctx), componentId, methodName, rawSignature);
    }

    private String deriveProjectName(ModuleExecutionContext ctx) {
        String repoPath = ctx.getAbsoluteRepoPath();
        if (repoPath == null || repoPath.isEmpty()) {
            return ctx.getModuleName();
        }
        int normalized = Math.max(repoPath.lastIndexOf('/'), repoPath.lastIndexOf('\\'));
        return normalized < 0 ? repoPath : repoPath.substring(normalized + 1);
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
