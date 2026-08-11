package com.canmakan.backend.product.scan;

/**
 * Represents a response from the barcode validation service.
 * 
 * @author Khai
 */

public record ValidationResponse(
    boolean validFood,
    String category,
    String message
) {}
