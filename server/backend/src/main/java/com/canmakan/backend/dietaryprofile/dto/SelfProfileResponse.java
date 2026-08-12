package com.canmakan.backend.dietaryprofile.dto;

import java.util.Map;

/** Newly created standalone SELF profile and its persisted restriction selections. */
public record SelfProfileResponse(
    Long profileId,
    String profileName,
    String relationship,
    boolean active,
    Map<Long, String> restrictions
) {
}
