package com.codewiki.agent.strategy;

import com.codewiki.agent.AgentExecutionResult;
import com.codewiki.context.ModuleExecutionContext;

public interface AgentStrategy {

    boolean supports(ModuleExecutionContext ctx);

    AgentExecutionResult execute(ModuleExecutionContext ctx);

    AgentExecutionResult executeWithFallback(ModuleExecutionContext ctx);
}
