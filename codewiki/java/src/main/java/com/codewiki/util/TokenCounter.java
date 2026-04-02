package com.codewiki.util;

import org.springframework.stereotype.Component;

/**
 * Approximate token counter using the GPT-4 heuristic (1 token ≈ 4 characters).
 *
 * A proper implementation would use the tiktoken library or a JNI binding.
 * The 4-character approximation is deliberately conservative: it over-counts
 * tokens for ASCII text, so the complexity threshold is hit slightly earlier
 * than with an exact counter – erring on the side of spawning sub-agents rather
 * than producing an excessively long prompt.
 *
 * Replace the body of count() with an exact tokeniser if needed.
 */
@Component
public class TokenCounter {

    private static final int CHARS_PER_TOKEN = 4;

    /**
     * Returns an approximate token count for the given text.
     * Returns 0 for null or empty input.
     */
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil((double) text.length() / CHARS_PER_TOKEN);
    }
}
