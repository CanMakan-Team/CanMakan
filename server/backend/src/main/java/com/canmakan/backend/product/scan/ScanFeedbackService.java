package com.canmakan.backend.product.scan;

import com.canmakan.backend.family.FamilyAuthorizationService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Persists thumbs up/down feedback reported against a scan verdict (UC20
 * report incorrect product info).
 *
 * @author Kwok Heng
 */
@Service
@RequiredArgsConstructor
public class ScanFeedbackService {

    private static final DateTimeFormatter CREATED_AT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ScanRepository scanRepository;
    private final ScanFeedbackRepository scanFeedbackRepository;
    private final FamilyAuthorizationService familyAuthorizationService;

    /**
     * Records a thumbs up/down against a scan. {@code userComments} is
     * optional: it is only ever expected alongside a thumbs down, but a
     * missing/blank comment is saved as a null comment either way.
     *
     * @param userId       the authenticated caller
     * @param scanId       the scan being reported on
     * @param isPositive   true for a thumbs up, false for a thumbs down
     * @param userComments free-text explanation, or null/blank if the user
     *                     did not elaborate
     */
    @Transactional
    public ScanFeedbackResponse submitFeedback(
            long userId, long scanId, boolean isPositive, String userComments) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ScanNotFoundException("Scan was not found."));

        // Reuse the same authorization rule as scanning/history: the caller
        // must own the scan's profile, or share a family with it.
        familyAuthorizationService.assertProfileAuthorizedForScan(userId, scan.getProfileId());

        ScanFeedback feedback = new ScanFeedback();
        feedback.setScanId(scanId);
        feedback.setPositive(isPositive);
        feedback.setUserComments(blankToNull(userComments));
        feedback.setResolved(false);
        feedback.setCreatedAt(LocalDateTime.now());

        ScanFeedback saved = scanFeedbackRepository.save(feedback);
        return toResponse(saved);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private ScanFeedbackResponse toResponse(ScanFeedback feedback) {
        LocalDateTime createdAt = feedback.getCreatedAt();
        return new ScanFeedbackResponse(
                feedback.getId(),
                feedback.getScanId(),
                feedback.isPositive(),
                feedback.getUserComments(),
                feedback.isResolved(),
                createdAt != null ? createdAt.truncatedTo(ChronoUnit.SECONDS).format(CREATED_AT_FORMATTER) : null
        );
    }
}
