package com.canmakan.backend.admin;

import com.canmakan.backend.admin.dto.AdminScanFeedbackListResponse;
import com.canmakan.backend.admin.dto.AdminScanFeedbackResponse;
import com.canmakan.backend.admin.dto.AdminScanFeedbackSummaryResponse;
import com.canmakan.backend.admin.dto.UpdateScanFeedbackResolvedResponse;
import com.canmakan.backend.admin.exception.AdminScanFeedbackNotFoundException;
import com.canmakan.backend.product.scan.AdminScanFeedbackView;
import com.canmakan.backend.product.scan.ScanFeedback;
import com.canmakan.backend.product.scan.ScanFeedbackRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read/update access to scan-verdict feedback for the System Admin "Handle
 * User Feedback" screen (UC20 admin review).
 *
 * @author Kwok Heng
 */
@Service
@RequiredArgsConstructor
public class AdminScanFeedbackService {

    /** Default date-period filter when the caller does not specify one ("Past month"). */
    static final int DEFAULT_PERIOD_DAYS = 30;

    private final ScanFeedbackRepository scanFeedbackRepository;

    /**
     * Lists feedback rows matching every supplied filter, plus summary stats
     * computed over that same filtered set. All filter parameters are
     * optional (null skips that filter); {@code periodDays} defaults to 30.
     */
    @Transactional(readOnly = true)
    public AdminScanFeedbackListResponse listFeedback(
            String keyword,
            String restrictionCode,
            Integer periodDays,
            Boolean isPositive,
            Boolean resolved
    ) {
        int resolvedPeriodDays = periodDays == null || periodDays <= 0
                ? DEFAULT_PERIOD_DAYS
                : periodDays;
        LocalDateTime since = LocalDateTime.now().minusDays(resolvedPeriodDays);

        List<AdminScanFeedbackResponse> items = scanFeedbackRepository.findForAdmin(
                since,
                blankToNull(keyword),
                blankToNull(restrictionCode),
                isPositive,
                resolved
        ).stream().map(AdminScanFeedbackService::toResponse).toList();

        return new AdminScanFeedbackListResponse(
                summarize(items, resolvedPeriodDays),
                items
        );
    }

    /** Updates the resolved flag on one feedback row. */
    @Transactional
    public UpdateScanFeedbackResolvedResponse updateResolved(Long feedbackId, boolean resolved) {
        ScanFeedback feedback = scanFeedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new AdminScanFeedbackNotFoundException(feedbackId));
        feedback.setResolved(resolved);
        ScanFeedback saved = scanFeedbackRepository.save(feedback);
        return new UpdateScanFeedbackResolvedResponse(saved.getId(), saved.isResolved());
    }

    private static AdminScanFeedbackSummaryResponse summarize(
            List<AdminScanFeedbackResponse> items, int periodDays) {
        long total = items.size();
        long negative = items.stream().filter(item -> !item.isPositive()).count();
        double negativePercentage = total == 0 ? 0.0 : (negative * 100.0) / total;
        double feedbackPerDay = (double) total / periodDays;
        double negativeFeedbackPerDay = (double) negative / periodDays;

        return new AdminScanFeedbackSummaryResponse(
                total,
                round(negativePercentage, 1),
                round(feedbackPerDay, 2),
                round(negativeFeedbackPerDay, 2)
        );
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static AdminScanFeedbackResponse toResponse(AdminScanFeedbackView view) {
        return new AdminScanFeedbackResponse(
                view.getId(),
                view.getScanId(),
                view.getUserEmail(),
                view.getProductName(),
                Boolean.TRUE.equals(view.getIsPositive()),
                view.getUserComments(),
                Boolean.TRUE.equals(view.getResolved()),
                view.getCreatedAt()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
