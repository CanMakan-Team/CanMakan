package com.canmakan.backend.admin.dto;

/**
 * Summary stats for the currently filtered set of scan feedback rows, shown
 * as the four cards above the "Handle User Feedback" table.
 *
 * @param totalFeedback           count of both positive and negative feedback
 * @param negativePercentage      share of {@code totalFeedback} that is negative, 0-100
 * @param feedbackPerDay          {@code totalFeedback} divided by the selected period's day count
 * @param negativeFeedbackPerDay  negative feedback count divided by the selected period's day count
 *
 * @author Kwok Heng
 */
public record AdminScanFeedbackSummaryResponse(
        long totalFeedback,
        double negativePercentage,
        double feedbackPerDay,
        double negativeFeedbackPerDay
) {
}
