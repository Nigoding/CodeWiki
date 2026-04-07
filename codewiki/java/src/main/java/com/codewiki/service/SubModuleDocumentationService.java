package com.codewiki.service;

import com.codewiki.agent.DocumentationOrchestrationService;
import com.codewiki.context.ModuleExecutionContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SubModuleDocumentationService {

    private final DocumentationOrchestrationService documentationOrchestrationService;

    public SubModuleDocumentationService(@Lazy DocumentationOrchestrationService documentationOrchestrationService) {
        this.documentationOrchestrationService = documentationOrchestrationService;
    }

    public Map<String, Object> generate(ModuleExecutionContext subContext) {
        return documentationOrchestrationService.processModuleContext(subContext);
    }
}
