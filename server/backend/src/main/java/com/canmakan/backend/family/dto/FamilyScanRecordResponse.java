package com.canmakan.backend.family.dto;

/**
 * One row of family-wide scan history for the Family Admin Portal's
 * "Family Scan History" screen (GET /api/families/me/scans). Field names
 * mirror the web dashboard's {@code ScanRecord} TypeScript type exactly,
 * since there is no shared schema to catch a mismatch.
 *
 * <p>{@code memberId} follows the same convention as {@link
 * FamilyMemberRosterDto}: a registered member's row uses their user id, and a
 * dependant's row uses their profile id.
 *
 * @author Amelia
 */
public record FamilyScanRecordResponse(
    Long scanId,
    String product,
    String brand,
    Long memberId,
    String evaluatedProfile,
    String verdict,               // SAFE / WARNING / AVOID
    String detectedIngredient,
    String resolvedIngredient,
    String matchedRestriction,
    String explanation,
    String dataCompleteness,      // COMPLETE / PARTIAL / PRODUCT_NOT_FOUND
    String dataSource,
    String scannedAt,             // ISO-8601, fixed "yyyy-MM-ddTHH:mm:ss" shape
    String suggestedAlternative
) {
}
