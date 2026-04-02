package com.codewiki.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent concurrency and rate-limiting properties.
 * Bound from application.yml under prefix "codewiki.agent.concurrency".
 */
@ConfigurationProperties(prefix = "codewiki.agent.concurrency")
public class AgentConcurrencyProperties {

    /**
     * Maximum number of sub-agent tasks that can run simultaneously.
     * This is the thread-pool core/max size; queue is bounded to prevent unbounded memory growth.
     */
    private int maxConcurrentSubAgents = 4;

    /**
     * Maximum number of LLM HTTP calls that can be in-flight at the same time.
     * A single sub-agent may trigger multiple LLM calls (agentic tool-calling loop),
     * so this is a tighter gate than the thread count above.
     */
    private int maxConcurrentLlmCalls = 3;

    /**
     * Capacity of the bounded task queue for the sub-agent thread pool.
     * When the queue is full the calling thread runs the task (CallerRunsPolicy).
     */
    private int threadPoolQueueCapacity = 20;

    /**
     * Timeout in minutes to wait for a rate-limit permit before aborting a sub-agent task.
     */
    private int rateLimitTimeoutMinutes = 5;

    public int getMaxConcurrentSubAgents() { return maxConcurrentSubAgents; }
    public void setMaxConcurrentSubAgents(int v) { this.maxConcurrentSubAgents = v; }

    public int getMaxConcurrentLlmCalls() { return maxConcurrentLlmCalls; }
    public void setMaxConcurrentLlmCalls(int v) { this.maxConcurrentLlmCalls = v; }

    public int getThreadPoolQueueCapacity() { return threadPoolQueueCapacity; }
    public void setThreadPoolQueueCapacity(int v) { this.threadPoolQueueCapacity = v; }

    public int getRateLimitTimeoutMinutes() { return rateLimitTimeoutMinutes; }
    public void setRateLimitTimeoutMinutes(int v) { this.rateLimitTimeoutMinutes = v; }
}
