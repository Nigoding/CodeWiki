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
import java.nio.file.Paths;

/**
 * Tool: read_code_components
 *
 * Allows the LLM agent to retrieve the full source code of any component
 * from the repository, including ones not in the initial coreComponentIds list.
 * This is the primary mechanism for the agent to "explore" indirect dependencies.
 *
 * Context flow
 * ────────────────────────────────────────────────────────────────────────────
 * ChatClient.toolContext(Map.of("executionContext", ctx))
 *   → Spring AI populates ToolContext
 *     → tool method receives ToolContext as last parameter
 *       → extracts ModuleExecutionContext
 *         → looks up Node in components map (O(1))
 *         → falls back to reading file from disk if needed
 */
@Component
public class ReadCodeComponentsTools {

    private static final Logger log = LoggerFactory.getLogger(ReadCodeComponentsTools.class);

    @Tool("""
            Read the full source code of a code component by its ID.
            Use this to explore dependencies that were not included in the initial context.
            Component ID format: "relative/path/to/file.ext::SymbolName"
            Returns the file content as a string.
            """)
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

        // Fall back: try to read the file whose path is embedded in the component ID
        // format "relative/path/file.ext::Symbol" → read file from repo root
        int sep = componentId.indexOf("::");
        String relPath = sep >= 0 ? componentId.substring(0, sep) : componentId;
        java.nio.file.Path filePath = Paths.get(ctx.getAbsoluteRepoPath(), relPath);

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
        Object raw = toolContext.getContext().get("executionContext");
        if (!(raw instanceof ModuleExecutionContext)) {
            throw new IllegalStateException(
                "ToolContext is missing 'executionContext' key. " +
                "Ensure ChatClient.toolContext(Map.of(\"executionContext\", ctx)) is called.");
        }
        return (ModuleExecutionContext) raw;
    }
}
