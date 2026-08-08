package com.canmakan.backend.family.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/families}.
 * Validation constraints are used to validate the request body.
 * 
 * @author Amelia
 */
public record CreateFamilyRequest(
        @NotBlank(message = "Family name is required.")
        @Size(max = 100, message = "Family name must be at most 100 characters.")
        String familyName
) {
}
