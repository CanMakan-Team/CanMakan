package com.canmakan.backend.family.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for {@code PUT /api/families/me/profiles/{profileId}} (UC12).
 * Restriction lists use catalog codes; omit them to leave selections unchanged.
 * When present, D3 applies (self linked profile or unlinked dependants only).
 * 
 * @author Amelia Wong
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateProfileRequest(
    @NotBlank(message = "Profile name is required.")
    @Size(max = 100, message = "Profile name must be at most 100 characters.")
    String profileName,

    @NotBlank(message = "Relationship is required.")
    @Size(max = 30, message = "Relationship must be at most 30 characters.")
    String relationship,

    List<String> commonRequirements,
    List<String> restrictions
) {
}
