package com.codewiki.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codewiki.prompt")
public class PromptContextProperties {

    private boolean preferSummaryContext = true;
    private int maxClassSummaries = 8;
    private int maxMethodSummaries = 12;
    private int fallbackSourceTokens = 800;

    public boolean isPreferSummaryContext() {
        return preferSummaryContext;
    }

    public void setPreferSummaryContext(boolean preferSummaryContext) {
        this.preferSummaryContext = preferSummaryContext;
    }

    public int getMaxClassSummaries() {
        return maxClassSummaries;
    }

    public void setMaxClassSummaries(int maxClassSummaries) {
        this.maxClassSummaries = maxClassSummaries;
    }

    public int getMaxMethodSummaries() {
        return maxMethodSummaries;
    }

    public void setMaxMethodSummaries(int maxMethodSummaries) {
        this.maxMethodSummaries = maxMethodSummaries;
    }

    public int getFallbackSourceTokens() {
        return fallbackSourceTokens;
    }

    public void setFallbackSourceTokens(int fallbackSourceTokens) {
        this.fallbackSourceTokens = fallbackSourceTokens;
    }
}
