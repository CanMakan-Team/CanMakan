package com.canmakan.backend.product.verdict;

import java.util.List;

/**
 * The outcome of a product safety assessment for one dietary profile:
 * a colour-coded level, a plain-language explanation, and the individual
 * {@link Finding}s that produced it.
 *
 * <p>Persistence mapping to {@code scans}: {@link Level#SAFE}/{@link Level#WARNING}
 * map directly; {@link Level#AVOID} is stored as {@code "UNSAFE"} in
 * {@code scans.verdict}. {@code explanation} maps to {@code scans.ai_explanation}
 * and {@code findings} to {@code scans.findings_json}.
 *
 * @author XieHuayuan
 */
public record SafetyVerdict(
        Level level,
        String explanation,
        List<Finding> findings
) {

    /** User-facing verdict, per the product module contract (Safe / Warning / Avoid). */
    public enum Level {
        SAFE,
        WARNING,
        AVOID
    }

    public static SafetyVerdict safe(String explanation, List<Finding> findings) {
        return new SafetyVerdict(Level.SAFE, explanation, findings);
    }

    public static SafetyVerdict warning(String explanation, List<Finding> findings) {
        return new SafetyVerdict(Level.WARNING, explanation, findings);
    }

    public static SafetyVerdict avoid(String explanation, List<Finding> findings) {
        return new SafetyVerdict(Level.AVOID, explanation, findings);
    }

    /** Value written to {@code scans.verdict} (AVOID persists as UNSAFE). */
    public String toScansVerdict() {
        return level == Level.AVOID ? "UNSAFE" : level.name();
    }
}
