package com.canmakan.backend.dietaryprofile.dto;

/**
 * Summary of a dietary profile for family profile lists.
 * 
 * @author Amelia Wong
 */
public record DietaryProfileSummaryDto(
        Long id,
        String profileName,
        Long familyId,
        String relationship,
        String initials,
        Boolean isPrimary) {
}
