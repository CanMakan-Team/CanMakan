package com.canmakan.backend.family.dto;

/**
 * Active scan profile for the authenticated user (UC11).
 *
 * @author Amelia
 */
public record ActiveProfileResponse(
    Long profileId,
    String profileName,
    String relationship,
    Long familyId,
    Boolean isPrimary
) {
}
