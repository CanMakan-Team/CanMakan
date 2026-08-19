package com.canmakan.backend.product.scan;

import jakarta.validation.constraints.NotBlank;

/**
 * Represents a barcode scan of a product.
 */

public record ScanRequest(@NotBlank String barcode) {
}