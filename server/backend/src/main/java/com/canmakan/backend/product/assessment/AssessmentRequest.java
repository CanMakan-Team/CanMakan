package com.canmakan.backend.product.assessment;

/**
 * Body of {@code POST /api/scan/assess}: scan one product for one dietary profile.
 * The scanning {@code userId} is taken from the auth token, not from the body.
 *
 * @author XieHuayuan
 */
public record AssessmentRequest(
        String barcode,
        Long profileId
) {
}
