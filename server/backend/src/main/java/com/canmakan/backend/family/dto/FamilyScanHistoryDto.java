package com.canmakan.backend.family.dto;

/**
 * Family-scoped scan history row for {@code GET /api/families/me/scans} (web portal).
 * Field names align with the web {@code ScanRecord} type.
 * {@code verdict} is {@code SAFE} | {@code WARNING} | {@code UNSAFE}.
 */
public record FamilyScanHistoryDto(
    long scanId,
    String product,
    String brand,
    long memberId,
    String evaluatedProfile,
    String verdict,
    String detectedIngredient,
    String resolvedIngredient,
    String matchedRestriction,
    String explanation,
    String dataCompleteness,
    String dataSource,
    String scannedAt,
    String suggestedAlternative
) {
}
