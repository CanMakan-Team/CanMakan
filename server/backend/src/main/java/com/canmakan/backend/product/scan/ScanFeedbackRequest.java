package com.canmakan.backend.product.scan;

/**
 * Request body for {@code POST /api/scan/{scanId}/feedback}. {@code isPositive}
 * is required (true for thumbs up, false for thumbs down); {@code userComments}
 * is optional free text and is only ever expected alongside a thumbs down, but
 * is accepted either way (UC20).
 *
 * <p>{@code isPositive} is boxed ({@link Boolean} rather than {@code boolean})
 * so a missing value can be told apart from an explicit {@code false} and
 * rejected with a clear 400 instead of silently defaulting.
 *
 * @author Kwok Heng
 */
public record ScanFeedbackRequest(Boolean isPositive, String userComments) {
}
