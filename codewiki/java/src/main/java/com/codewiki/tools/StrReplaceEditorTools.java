package com.codewiki.tools;

import com.codewiki.context.ModuleExecutionContext;
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

/**
 * Tool: str_replace_editor
 *
 * Provides file-system operations for documentation files inside the working
 * (docs output) directory.  The LLM agent uses this to create new files and
 * to make targeted edits without rewriting entire documents.
 *
 * All path arguments from the LLM are treated as relative to absoluteDocsPath;
 * path traversal characters are rejected to prevent writes outside the docs dir.
 */
@Component
public class StrReplaceEditorTools {

    private static final Logger log = LoggerFactory.getLogger(StrReplaceEditorTools.class);

    // ── Tool methods ──────────────────────────────────────────────────────────

    @Tool("""
            Create a new documentation file or completely overwrite an existing one.
            The file is saved inside the documentation output directory.
            fileName must be a plain file name such as "auth_module.md" (no path separators).
            """)
    public String writeFile(
            @ToolParam(description = "File name, e.g. agent_orchestrator.md") String fileName,
            @ToolParam(description = "Full markdown content to write")          String content,
            ToolContext toolContext) {

        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        Path target = resolveAndValidate(ctx.getAbsoluteDocsPath(), fileName);

        try {
            ensureParentExists(target);
            Files.write(target, content.getBytes(StandardCharsets.UTF_8));
            log.info("writeFile: created {}", target);
            return "File written: " + fileName;
        } catch (IOException e) {
            log.error("writeFile: failed to write {}: {}", target, e.getMessage());
            return "Error writing file: " + e.getMessage();
        }
    }

    @Tool("""
            Replace an exact substring in an existing documentation file.
            The match must be unique within the file; if oldStr appears more than once
            the first occurrence is replaced.
            Use this for targeted edits rather than rewriting the whole file.
            """)
    public String replaceInFile(
            @ToolParam(description = "File name, e.g. agent_orchestrator.md") String fileName,
            @ToolParam(description = "Exact string to find (must exist in file)") String oldStr,
            @ToolParam(description = "Replacement string")                        String newStr,
            ToolContext toolContext) {

        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        Path target = resolveAndValidate(ctx.getAbsoluteDocsPath(), fileName);

        if (!Files.exists(target)) {
            return "Error: file not found: " + fileName;
        }

        try {
            String original = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
            if (!original.contains(oldStr)) {
                return "Error: oldStr not found in " + fileName;
            }
            String updated = original.replace(oldStr, newStr);
            Files.write(target, updated.getBytes(StandardCharsets.UTF_8));
            log.debug("replaceInFile: patched {}", target);
            return "Replaced in: " + fileName;
        } catch (IOException e) {
            log.error("replaceInFile: failed to patch {}: {}", target, e.getMessage());
            return "Error patching file: " + e.getMessage();
        }
    }

    @Tool("""
            Read the current content of a documentation file.
            Use this to inspect an existing file before making edits.
            """)
    public String readFile(
            @ToolParam(description = "File name to read, e.g. auth_module.md") String fileName,
            ToolContext toolContext) {

        ModuleExecutionContext ctx = ReadCodeComponentsTools.extractContext(toolContext);
        Path target = resolveAndValidate(ctx.getAbsoluteDocsPath(), fileName);

        if (!Files.exists(target)) {
            return "File not found: " + fileName;
        }
        try {
            return new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves fileName relative to docsPath and rejects path traversal.
     */
    private Path resolveAndValidate(String docsPath, String fileName) {
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new SecurityException(
                "Invalid fileName '" + fileName + "': path separators and '..' are not allowed.");
        }
        return Paths.get(docsPath, fileName).toAbsolutePath().normalize();
    }

    private void ensureParentExists(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }
}
