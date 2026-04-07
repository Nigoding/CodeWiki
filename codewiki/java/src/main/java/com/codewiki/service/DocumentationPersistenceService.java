package com.codewiki.service;

import com.codewiki.agent.AgentExecutionResult;
import com.codewiki.config.AgentProperties;
import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.ModuleTask;
import com.codewiki.tree.ModuleTreeManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class DocumentationPersistenceService {

    private final AgentProperties agentProperties;

    public DocumentationPersistenceService(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    public boolean moduleDocExists(String docsPath, String moduleName) {
        return Files.exists(Paths.get(docsPath, moduleName + ".md"));
    }

    public void persist(ModuleTask task,
                        ModuleExecutionContext context,
                        AgentExecutionResult result,
                        ModuleTreeManager treeManager) {
        if (!result.isDocumentWrittenByTool()
                && result.getAssistantContent() != null
                && !result.getAssistantContent().trim().isEmpty()) {
            writeModuleDoc(task.getDocsPath(), task.getModuleName(), result.getAssistantContent());
        }
        treeManager.saveToFile(task.getDocsPath(), agentProperties.getModuleTreeFilename());
    }

    private void writeModuleDoc(String docsPath, String moduleName, String content) {
        Path target = Paths.get(docsPath, moduleName + ".md").toAbsolutePath().normalize();
        try {
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.write(target, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist generated markdown: " + target, e);
        }
    }
}
