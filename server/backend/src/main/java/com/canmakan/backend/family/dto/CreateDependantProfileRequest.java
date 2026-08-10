package com.canmakan.backend.family.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Create a dependant dietary profile (no login account / no family_members row).
 * Restriction lists use catalog codes; unknown fields such as ageGroup are ignored.
 * 
 * @author Amelia
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateDependantProfileRequest(
    @NotBlank(message = "Profile name is required.")
    @Size(max = 100, message = "Profile name must be at most 100 characters.")
    String profileName,

    @NotBlank(message = "Relationship is required.")
    @Size(max = 30, message = "Relationship must be at most 30 characters.")
    String relationship,

    List<String> commonRequirements,
    List<String> restrictions
) {
    public CreateDependantProfileRequest {
        profileName = profileName == null ? null : profileName.strip();
        relationship = relationship == null ? null : relationship.strip();
    }
}
