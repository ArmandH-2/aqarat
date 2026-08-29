package co.syntropyhq.aqarat.ai;

import java.util.concurrent.atomic.AtomicLong;

/**
 * What the assistant has cost this session, in tokens.
 *
 * <p>"How much does the AI cost to run" is answerable with a measurement or with
 * a guess, and only one of those is engineering. Every provider returns a
 * {@code usage} block on each completion; this keeps the running totals so the
 * answer is a number the application observed rather than an estimate.
 *
 * <p>Counts calls as well as tokens, because the two together are what show the
 * router working: searches rise while calls stay flat.
 */
public final class TokenLedger {

    private static final AtomicLong CALLS = new AtomicLong();
    private static final AtomicLong PROMPT_TOKENS = new AtomicLong();
    private static final AtomicLong COMPLETION_TOKENS = new AtomicLong();

    private TokenLedger() {
    }

    /** Records one completion. Missing counts are recorded as zero, not guessed. */
    public static void record(long promptTokens, long completionTokens) {
        CALLS.incrementAndGet();
        PROMPT_TOKENS.addAndGet(Math.max(0, promptTokens));
        COMPLETION_TOKENS.addAndGet(Math.max(0, completionTokens));
    }

    public static long calls() {
        return CALLS.get();
    }

    public static long promptTokens() {
        return PROMPT_TOKENS.get();
    }

    public static long completionTokens() {
        return COMPLETION_TOKENS.get();
    }

    public static long totalTokens() {
        return PROMPT_TOKENS.get() + COMPLETION_TOKENS.get();
    }

    /** One line for the console, and for anyone asking what this feature spends. */
    public static String summary() {
        long calls = CALLS.get();
        if (calls == 0) {
            return "Assistant: no model calls this session.";
        }
        return "Assistant: %d model call%s, %d prompt + %d completion = %d tokens (%d avg per call)."
            .formatted(calls, calls == 1 ? "" : "s",
                PROMPT_TOKENS.get(), COMPLETION_TOKENS.get(), totalTokens(), totalTokens() / calls);
    }
}
