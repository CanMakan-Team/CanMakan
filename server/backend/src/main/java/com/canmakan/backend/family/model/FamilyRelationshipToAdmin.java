package com.canmakan.backend.family.model;

/**
 * Relationship of a dependant or invited member to the family PRIMARY_ADMIN.
 * Linked admin profiles stay {@code SELF}; invitees use one of these values.
 */
public final class FamilyRelationshipToAdmin {

    public static final String PATTERN = "SPOUSE|CHILD|PARENT|DEPENDANT|OTHER";

    private FamilyRelationshipToAdmin() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.strip().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}
