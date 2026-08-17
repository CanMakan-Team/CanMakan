package com.canmakan.backend.product.scan;

/**
 * Response for a submitted negative scan-verdict report (UC20). Field names
 * are chosen to match the Android app's {@code ScanFeedbackResponse} Kotlin
 * data class exactly, since Gson maps JSON keys to Kotlin properties by name.
 *
 * @author Kwok Heng
 */
public record ScanFeedbackResponse(
        Long id,
        Long scanId,
        boolean isPositive,
        String userComments,
        boolean resolved,
        String createdAt      // ISO-8601, "yyyy-MM-ddTHH:mm:ss"
) {
}
