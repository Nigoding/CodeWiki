package com.codewiki.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrency infrastructure beans for sub-agent parallelism and LLM rate-limiting.
 *
 * Design rationale
 * ──────────────────────────────────────────────────────────────────────────────
 * Sub-agent execution uses two independent throttles that complement each other:
 *
 * 1. subAgentExecutor (ThreadPoolExecutor)
 *    Limits the number of concurrently active sub-agent *tasks*.
 *    A sub-agent task may include file I/O, prompt building, and one or more
 *    round-trips to the LLM.  CallerRunsPolicy provides natural back-pressure:
 *    once the queue fills the calling thread (i.e. the parent agent's tool call)
 *    blocks instead of spawning unboundedly.
 *
 * 2. llmRateLimitSemaphore (Semaphore)
 *    Limits the number of *in-flight LLM HTTP requests* across all threads.
 *    A single agent task may make multiple sequential LLM calls (tool-use loop),
 *    so this semaphore is the finer-grained control that prevents API quota
 *    exhaustion regardless of how the thread pool is configured.
 *
 * Typical relationship: maxConcurrentSubAgents >= maxConcurrentLlmCalls
 */
@Configuration
@EnableConfigurationProperties(AgentConcurrencyProperties.class)
public class AgentConcurrencyConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConcurrencyConfig.class);

    /**
     * Bounded, fixed-size thread pool dedicated to sub-agent work.
     * The bean is named explicitly so it can be injected by name alongside
     * other ExecutorService beans if the application later adds more pools.
     *
     * destroyMethod = "shutdown" ensures threads are cleaned up on context close.
     */
    @Bean(name = "subAgentExecutor", destroyMethod = "shutdown")
    public ExecutorService subAgentExecutor(AgentConcurrencyProperties props) {
        int size = props.getMaxConcurrentSubAgents();
        int queueCap = props.getThreadPoolQueueCapacity();

        log.info("Creating sub-agent thread pool: size={}, queueCapacity={}", size, queueCap);

        return new ThreadPoolExecutor(
                size,
                size,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCap),
                new NamedDaemonThreadFactory("sub-agent"),
                // CallerRunsPolicy: the thread that submitted the task (parent agent) runs it
                // directly when the pool is saturated – natural back-pressure, never drops tasks.
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Fair semaphore that gates the number of concurrent LLM API calls.
     * fair=true prevents starvation when many threads compete for permits.
     */
    @Bean
    public Semaphore llmRateLimitSemaphore(AgentConcurrencyProperties props) {
        int permits = props.getMaxConcurrentLlmCalls();
        log.info("Creating LLM rate-limit semaphore: permits={}", permits);
        return new Semaphore(permits, /* fair */ true);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static final class NamedDaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(0);

        NamedDaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
