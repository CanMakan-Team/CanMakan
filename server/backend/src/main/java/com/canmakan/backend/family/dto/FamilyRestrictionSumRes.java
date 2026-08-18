package com.canmakan.backend.family.dto;

import java.util.List;

/**
 * UC6: Family Dietary Restrictions Summary Response DTO.
 * Top-level response payload for the Family Allergy Summary grid.
 * Contains a list of FamilyMeRestrictionSum objects, each representing
 * a family member and their respective dietary restrictions.
 */
public record FamilyRestrictionSumRes(
        List<FamilyMeRestrictionSum> familyMembers
) {
}
