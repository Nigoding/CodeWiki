package com.codewiki.exception;

/**
 * Thrown when an agent fails to generate documentation for a module,
 * even after the fallback model has been attempted.
 */
public class DocumentationGenerationException extends RuntimeException {

    private final String moduleName;

    public DocumentationGenerationException(String moduleName, Throwable cause) {
        super("Documentation generation failed for module: " + moduleName, cause);
        this.moduleName = moduleName;
    }

    public DocumentationGenerationException(String moduleName, String message) {
        super("Documentation generation failed for module '" + moduleName + "': " + message);
        this.moduleName = moduleName;
    }

    public String getModuleName() {
        return moduleName;
    }
}
