package com.canmakan.backend.product.verdict;

/**
 * Describes whether a finding is a confirmed violation or an uncertainty.
 *
 * @author YangMaowei
 */
public enum FindingType {
    CONFIRMED_CONFLICT(true),
    THRESHOLD_EXCEEDED(true),
    MISSING_CERTIFICATION(false),
    INCOMPLETE_DATA(false),
    UNAVAILABLE_NUTRITION(false),
    INVALID_NUTRITION(false),
    UNRESOLVED_INGREDIENT(false),
    MODEL_EVIDENCE(false);

    private final boolean confirmedViolation;

    FindingType(boolean confirmedViolation) {
        this.confirmedViolation = confirmedViolation;
    }

    public boolean isConfirmedViolation() {
        return confirmedViolation;
    }
}
