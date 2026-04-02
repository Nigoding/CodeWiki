package com.codewiki.domain;

/**
 * Represents a single code component (class, function, or file-level symbol)
 * extracted from the source repository.
 *
 * Mirrors the Python Node model from dependency_analyzer.models.core.
 */
public class Node {

    /** Unique identifier: "relative/path/to/file.py::SymbolName" */
    private String id;

    /** Absolute path to the source file on disk */
    private String filePath;

    /** Path relative to the repository root */
    private String relativePath;

    /** Full source text of this component */
    private String content;

    /** Human-readable display name */
    private String name;

    public Node() {}

    public Node(String id, String filePath, String relativePath, String content, String name) {
        this.id = id;
        this.filePath = filePath;
        this.relativePath = relativePath;
        this.content = content;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
