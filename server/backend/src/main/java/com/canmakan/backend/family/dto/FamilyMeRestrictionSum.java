package com.canmakan.backend.family.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC6: Family Members' Dietary Restrictions Summary DTO.
 * Aggregates the basic and active dietary profiles of each family member in the family circle.
 * Serves as a single row or column entity for matrix-style display of family members and their dietary restrictions.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FamilyMeRestrictionSum {
    /** Linked account user id; {@code 0} for dependant profiles without a login. */
    private Long userId;
    /** Dietary profile id (members and dependants). */
    private Long profileId;
    private String name;
    private Boolean isActive;
    private List<FamilyMeRestrictionDetail> restrictions;
}
