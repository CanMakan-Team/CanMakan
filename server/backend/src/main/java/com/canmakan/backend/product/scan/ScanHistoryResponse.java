package com.canmakan.backend.product.scan;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One row of scan history for the "view scan verdicts" screen. This is a
 * dedicated API-boundary DTO, not the {@link Scan}/{@link
 * com.canmakan.backend.product.model.ScanProduct} JPA entities directly — its
 * field names are chosen to match the Android app's {@code ScanHistoryEntry}
 * Kotlin data class exactly, since Gson maps JSON keys to Kotlin properties by
 * name with no shared schema to catch a mismatch.
 *
 * @author XieHuayuan
 */
public record ScanHistoryResponse(
        Long id,
        Long profileId,
        String barcode,
        ProductDto product,
        String scannedAt,        // ISO-8601, fixed "yyyy-MM-ddTHH:mm:ss" shape (see ScanHistoryService#formatScannedAt)
        String verdict,          // SAFE / WARNING / UNSAFE
        FindingsDto findingsJson,
        String aiExplanation
) {

    /** Mirrors the Kotlin {@code Product} data class (productName, brand, barcode). */
    public record ProductDto(String productName, String brand, String barcode) {
    }

    /**
     * Mirrors the Kotlin {@code FindingsJson} data class. 
     * side changes.
     */
    public record FindingsDto(
            @JsonProperty("matched_rules") List<String> matchedRules,
            @JsonProperty("allergens_found") List<String> allergensFound
    ) {
    }
}
