package com.canmakan.backend.dietaryprofile.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** Authenticated request for creating the caller's linked SELF profile. */
public record CreateSelfProfileRequest(
    @NotBlank(message = "Profile name is required.")
    @Size(max = 100, message = "Profile name must not exceed 100 characters.")
    String profileName,

    Map<Long, String> restrictions
) {

    public CreateSelfProfileRequest {
        profileName = profileName == null ? null : profileName.strip();
        restrictions = restrictions == null ? Map.of() : Map.copyOf(restrictions);
    }

    /** Reject identity or profile fields outside this narrow setup contract. */
    @JsonAnySetter
    public void rejectUnknownProperty(String propertyName, Object ignoredValue) {
        throw new IllegalArgumentException("Unsupported SELF profile field: " + propertyName);
    }
}
