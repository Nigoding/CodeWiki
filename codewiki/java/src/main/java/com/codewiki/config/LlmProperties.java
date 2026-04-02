package com.codewiki.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM model configuration properties.
 * Bound from application.yml under prefix "codewiki.llm".
 */
@ConfigurationProperties(prefix = "codewiki.llm")
public class LlmProperties {

    /** Primary model name, e.g. "gpt-4o" */
    private String primaryModel = "gpt-4o";

    /** Fallback model name used when the primary fails, e.g. "gpt-4o-mini" */
    private String fallbackModel = "gpt-4o-mini";

    /** Base URL for OpenAI-compatible endpoint */
    private String baseUrl = "https://api.openai.com/v1";

    /** API key */
    private String apiKey;

    /** Max output tokens per LLM call */
    private int maxTokens = 8192;

    /** Sampling temperature; 0.0 for deterministic output */
    private double temperature = 0.0;

    public String getPrimaryModel() { return primaryModel; }
    public void setPrimaryModel(String primaryModel) { this.primaryModel = primaryModel; }

    public String getFallbackModel() { return fallbackModel; }
    public void setFallbackModel(String fallbackModel) { this.fallbackModel = fallbackModel; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
}
