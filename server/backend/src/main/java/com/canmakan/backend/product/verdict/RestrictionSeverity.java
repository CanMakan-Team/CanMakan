package com.canmakan.backend.product.verdict;

/**
 * How strictly a restriction must be enforced, mirroring
 * {@code profile_restrictions.severity_level}.
 *
 * <ul>
 *   <li>{@link #STRICT_AVOID} - a violation must produce an AVOID verdict.</li>
 *   <li>{@link #INTOLERANCE} - a violation produces a WARNING (tolerable in small amounts).</li>
 * </ul>
 *
 * @author XieHuayuan
 */
public enum RestrictionSeverity {
    STRICT_AVOID,
    INTOLERANCE
}
