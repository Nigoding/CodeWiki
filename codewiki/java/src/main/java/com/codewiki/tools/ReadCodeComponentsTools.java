package com.codewiki.tools;

import com.codewiki.context.ModuleExecutionContext;
import com.codewiki.domain.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ReadCodeComponentsTools {

    private static final Logger log = LoggerFactory.getLogger(ReadCodeComponentsTools.class);

    @Tool(
            "Read the full source code of a code component by its ID. "
                    + "Use this to explore dependencies that were not included in the initial context. "
                    + "Component ID format: \"relative/path/to/file.ext::SymbolName\". "
                    + "Returns the file content as a string."
    )
    public String readCodeComponents(
            @ToolParam(description = "Component ID, e.g. src/auth/handler.py::AuthHandler")
            String componentId,
            ToolContext toolContext) {

        ModuleExecutionContext ctx = extractContext(toolContext);
        Node node = ctx.getComponents().get(componentId);

        if (node != null && node.getContent() != null) {
            log.debug("readCodeComponents: found in context registry: {}", componentId);
            return node.getContent();
        }

        int sep = componentId.indexOf("::");
        String relativePath = sep >= 0 ? componentId.substring(0, sep) : componentId;
        Path filePath = resolveRepoFile(ctx, relativePath);

        if (Files.exists(filePath)) {
            try {
                String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                log.debug("readCodeComponents: read from disk: {}", filePath);
                return content;
            } catch (IOException e) {
                log.warn("readCodeComponents: could not read file {}: {}", filePath, e.getMessage());
                return "Error reading file: " + e.getMessage();
            }
        }

        log.warn("readCodeComponents: component not found: {}", componentId);
        return "Component not found: " + componentId;
    }

    static ModuleExecutionContext extractContext(ToolContext toolContext) {
        Object raw = toolContext.getContext().get(GenerateSubModuleDocTools.CTX_KEY);
        if (!(raw instanceof ModuleExecutionContext)) {
            throw new IllegalStateException(
                    "ToolContext is missing 'executionContext'. "
                            + "Ensure ChatClient.toolContext(Collections.singletonMap(\"executionContext\", ctx)) is called.");
        }
        return (ModuleExecutionContext) raw;
    }

    private Path resolveRepoFile(ModuleExecutionContext context, String relativePath) {
        Path repoRoot = Paths.get(context.getAbsoluteRepoPath()).toAbsolutePath().normalize();
        Path target = repoRoot.resolve(relativePath).normalize();
        if (!target.startsWith(repoRoot)) {
            throw new SecurityException("Path escapes repository root: " + relativePath);
        }
        return target;
    }
}
