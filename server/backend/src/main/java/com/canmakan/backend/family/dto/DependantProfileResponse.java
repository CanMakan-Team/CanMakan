package com.canmakan.backend.family.dto;

/** Response after creating a dependant dietary profile. */
public record DependantProfileResponse(
    Long profileId,
    String profileName,
    String relationship,
    Long familyId
) {
}
