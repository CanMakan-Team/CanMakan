package com.canmakan.backend.product.verdict;

/**
 * How strictly a restriction must be enforced, mirroring
 * {@code profile_restrictions.severity_level}.
 *
 * <ul>
 *   <li>{@link #STRICT_AVOID} - a confirmed violation produces an UNSAFE verdict.</li>
 *   <li>{@link #INTOLERANCE} - a violation produces a WARNING (tolerable in small amounts).</li>
 *   <li>{@link #PREFERENCE} - a preference violation produces a WARNING.</li>
 * </ul>
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
public enum RestrictionSeverity {
    STRICT_AVOID,
    INTOLERANCE,
    PREFERENCE
}
