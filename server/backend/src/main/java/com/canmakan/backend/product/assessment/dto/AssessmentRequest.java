package com.canmakan.backend.product.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/scan/assess}: scan one product for one dietary profile.
 * The scanning {@code userId} is taken from the auth token, not from the body.
 *
 * @author XieHuayuan
 */
public record AssessmentRequest(
        @NotBlank(message = "Product Barcode is required")
        String barcode,
        @NotNull(message = "Profile ID is required")
        Long profileId
) {
}
