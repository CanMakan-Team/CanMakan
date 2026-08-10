package com.canmakan.backend.family.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for PUT /api/families/me/active-profile (UC11).
 *
 * @author Amelia
 */
public record SetActiveProfileRequest(
    @NotNull(message = "Profile id is required.")
    Long profileId
) {
}
