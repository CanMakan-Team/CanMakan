package com.canmakan.backend.family.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC6: Family Dietary Restrictions Summary Response DTO.
 * Top-level response payload for the Family Allergy Summary grid.
 * Contains a list of FamilyMeRestrictionSum objects, each representing 
 * a family member and their respective dietary restrictions.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FamilyRestrictionSumRes {
    private List<FamilyMeRestrictionSum> familyMembers;
}
