package com.canmakan.backend.family.dto;

import java.util.List;

/**
 * UC6: Family Members' Dietary Restrictions Summary DTO.
 * Aggregates the basic and active dietary profiles of each family member in the family circle.
 * Serves as a single row or column entity for matrix-style display of family members and their dietary restrictions.
 */
public record FamilyMeRestrictionSum(
        /** Linked account user id; {@code 0} for dependant profiles without a login. */
        Long userId,
        /** Dietary profile id (members and dependants). */
        Long profileId,
        String name,
        Boolean isActive,
        List<FamilyMeRestrictionDetail> restrictions
) {
}
