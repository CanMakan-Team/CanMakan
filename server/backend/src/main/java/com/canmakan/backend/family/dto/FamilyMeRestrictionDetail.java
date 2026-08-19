package com.canmakan.backend.family.dto;

/**
 * UC6: Restriction detail DTO for Family Member Dietary Restrictions.
 * Represents a specific dietary restriction detail associated with a family member.
 * Provides the required display and severity details to render for the data points
 * within the family allergy summary grid.
 */
public record FamilyMeRestrictionDetail(
        String code,
        String displayName,
        String severity
) {
}
