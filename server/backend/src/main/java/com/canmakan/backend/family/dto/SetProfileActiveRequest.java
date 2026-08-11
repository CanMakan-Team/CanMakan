package com.canmakan.backend.family.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/families/me/profiles/{profileId}} (UC12).
 * 
 * @author Amelia Wong
 */
public record SetProfileActiveRequest(
    @NotNull(message = "active is required.")
    Boolean active // true if profile is active
) {
}
