package com.codewiki.tools;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.util.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ToolDecisionTools {

    private static final Logger log = LoggerFactory.getLogger(ToolDecisionTools.class);

    @Tool(
            "Record a visible tool decision before calling or skipping information-gathering tools. "
                    + "Use this for concise, evidence-based reasons only, not hidden chain-of-thought."
    )
    public String record_tool_decision(
            @ToolParam(description = "Tool being considered: recall_summary, read_code_components, generate_sub_module_documentation, or none")
            String toolName,
            @ToolParam(description = "Decision action: call or skip")
            String action,
            @ToolParam(description = "Documentation section or workflow step this decision supports")
            String targetSection,
            @ToolParam(description = "Short evidence-based reason visible from the current context")
            String reason,
            @ToolParam(description = "Key planned parameters, or empty when not applicable")
            String params,
            ToolContext toolContext) {

        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        String normalizedTool = Texts.trimToEmpty(toolName);
        String normalizedAction = Texts.trimToEmpty(action);
        String normalizedSection = Texts.trimToEmpty(targetSection);
        String normalizedReason = Texts.trimToEmpty(reason);
        String normalizedParams = Texts.trimToEmpty(params);

        log.info("[ToolDecision] module={} tool={} action={} section={} params={} reason={}",
                ctx.getModuleName(),
                normalizedTool.isEmpty() ? "none" : normalizedTool,
                normalizedAction.isEmpty() ? "skip" : normalizedAction,
                normalizedSection,
                normalizedParams,
                normalizedReason);

        return "Tool decision recorded.";
    }
}
