package com.codewiki.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    /** Fully qualified class name for this component */
    private String classFqn;

    /** Fully qualified method names owned by this component */
    private List<String> methodFqns = new ArrayList<String>();

    /** Fully qualified package names associated with this component */
    private List<String> packageFqns = new ArrayList<String>();

    public Node() {}

    public Node(String id, String filePath, String relativePath, String content, String name) {
        this.id = id;
        this.filePath = filePath;
        this.relativePath = relativePath;
        this.content = content;
        this.name = name;
    }

    public Node(String id,
                String filePath,
                String relativePath,
                String content,
                String name,
                String classFqn,
                List<String> methodFqns,
                List<String> packageFqns) {
        this.id = id;
        this.filePath = filePath;
        this.relativePath = relativePath;
        this.content = content;
        this.name = name;
        this.classFqn = classFqn;
        this.methodFqns = methodFqns == null ? new ArrayList<String>() : new ArrayList<String>(methodFqns);
        this.packageFqns = packageFqns == null ? new ArrayList<String>() : new ArrayList<String>(packageFqns);
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

    public String getClassFqn() { return classFqn; }
    public void setClassFqn(String classFqn) { this.classFqn = classFqn; }

    public List<String> getMethodFqns() { return Collections.unmodifiableList(methodFqns); }
    public void setMethodFqns(List<String> methodFqns) {
        this.methodFqns = methodFqns == null ? new ArrayList<String>() : new ArrayList<String>(methodFqns);
    }

    public List<String> getPackageFqns() { return Collections.unmodifiableList(packageFqns); }
    public void setPackageFqns(List<String> packageFqns) {
        this.packageFqns = packageFqns == null ? new ArrayList<String>() : new ArrayList<String>(packageFqns);
    }
}
