package com.canmakan.backend.product.scan;

/**
 * Represents a response from the barcode validation service.
 */

public record ValidationResponse(
    boolean validFood,
    String category,
    String message
) {}
