package com.codewiki.agent;

public class AgentExecutionResult {

    private final String assistantContent;
    private final boolean documentWrittenByTool;
    private final boolean usedFallback;

    public AgentExecutionResult(String assistantContent,
                                boolean documentWrittenByTool,
                                boolean usedFallback) {
        this.assistantContent = assistantContent;
        this.documentWrittenByTool = documentWrittenByTool;
        this.usedFallback = usedFallback;
    }

    public String getAssistantContent() {
        return assistantContent;
    }

    public boolean isDocumentWrittenByTool() {
        return documentWrittenByTool;
    }

    public boolean isUsedFallback() {
        return usedFallback;
    }
}
