package com.canmakan.backend.admin.dto;

import java.time.LocalDateTime;

/**
 * One row of scan-verdict feedback for the System Admin "Handle User
 * Feedback" screen (UC20 admin review).
 *
 * @author Kwok Heng
 */
public record AdminScanFeedbackResponse(
        Long id,
        Long scanId,
        String userEmail,
        String productName,
        boolean isPositive,
        String userComments,
        boolean resolved,
        LocalDateTime createdAt
) {
}
