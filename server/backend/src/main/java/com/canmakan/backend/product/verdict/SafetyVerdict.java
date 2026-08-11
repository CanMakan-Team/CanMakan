package com.canmakan.backend.product.verdict;

import java.util.List;

/**
 * The outcome of a product safety assessment for one dietary profile:
 * a colour-coded level, a plain-language explanation, and the individual
 * {@link Finding}s that produced it.
 *
 * <p>Persistence mapping to {@code scans}: {@code level} maps directly to
 * {@code scans.verdict} (SAFE / WARNING / UNSAFE); {@code explanation} maps to
 * {@code scans.ai_explanation} and {@code findings} to {@code scans.findings_json}.
 *
 * @author XieHuayuan
 */
public record SafetyVerdict(
        Level level,
        String explanation,
        List<Finding> findings
) {

    /**
     * Verdict level, aligned with {@code scans.verdict}
     * ({@code SAFE} / {@code WARNING} / {@code UNSAFE}).
     */
    public enum Level {
        SAFE,
        WARNING,
        UNSAFE
    }

    public static SafetyVerdict safe(String explanation, List<Finding> findings) {
        return new SafetyVerdict(Level.SAFE, explanation, findings);
    }

    public static SafetyVerdict warning(String explanation, List<Finding> findings) {
        return new SafetyVerdict(Level.WARNING, explanation, findings);
    }

    public static SafetyVerdict unsafe(String explanation, List<Finding> findings) {
        return new SafetyVerdict(Level.UNSAFE, explanation, findings);
    }

    /** Value written to {@code scans.verdict}. */
    public String toScansVerdict() {
        return level.name();
    }
}
