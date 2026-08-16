package com.canmakan.backend.admin.dto;

import java.util.List;

/**
 * Response for {@code GET /api/admin/scan-feedback}: summary cards and the
 * matching table rows for the same filtered set.
 *
 * @author Kwok Heng
 */
public record AdminScanFeedbackListResponse(
        AdminScanFeedbackSummaryResponse summary,
        List<AdminScanFeedbackResponse> items
) {
}
