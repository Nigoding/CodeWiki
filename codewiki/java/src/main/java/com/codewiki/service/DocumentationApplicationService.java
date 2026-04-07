package com.codewiki.service;

import com.codewiki.agent.DocumentationOrchestrationService;
import com.codewiki.domain.ModuleTask;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DocumentationApplicationService {

    private final DocumentationOrchestrationService documentationOrchestrationService;

    public DocumentationApplicationService(DocumentationOrchestrationService documentationOrchestrationService) {
        this.documentationOrchestrationService = documentationOrchestrationService;
    }

    public Map<String, Object> generateModule(ModuleTask task) {
        return documentationOrchestrationService.processModule(task);
    }
}
