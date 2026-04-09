package com.codewiki.summary;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.summary.dto.ModuleBrief;

public interface ModuleBriefBuilder {

    ModuleBrief build(ModuleExecutionContext ctx);
}
