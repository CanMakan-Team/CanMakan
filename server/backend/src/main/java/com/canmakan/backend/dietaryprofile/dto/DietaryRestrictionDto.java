package com.canmakan.backend.dietaryprofile.dto;

/**
 * API response for a row in the dietary restriction catalog.
 * 
 * @author Amelia Wong
 */
public record DietaryRestrictionDto(
        Long id,
        String code,
        String displayName,
        String category,
        String description) {
}
