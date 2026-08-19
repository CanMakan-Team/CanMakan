package com.canmakan.backend.dietaryprofile.dto;

/**
 * Summary of a dietary profile for family profile lists.
 * 
 * @author Amelia Wong
 */
public record DietaryProfileSummaryDto(
        Long id, // id of the profile
        String profileName, // name of the profile
        Long familyId, // id of the family
        String relationship, // SON, DAUGHTER, FATHER, MOTHER, etc.
        String initials, // initials of the profile name
        Boolean isPrimary, // true if this profile belongs to the family PRIMARY_ADMIN
        Boolean active // true if profile is active
) {
}
