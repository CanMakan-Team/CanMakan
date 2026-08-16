package com.canmakan.backend.admin.dto;

import java.util.List;

/**
 * Response for {@code GET /api/admin/scan-feedback}: summary cards computed
 * over the full filtered set, one page of matching rows, and the pagination
 * metadata needed to fetch the rest.
 *
 * @author Kwok Heng
 */
public record AdminScanFeedbackListResponse(
        AdminScanFeedbackSummaryResponse summary,
        List<AdminScanFeedbackResponse> items,
        AdminScanFeedbackPageInfo pageInfo
) {
}
