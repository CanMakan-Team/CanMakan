package com.canmakan.backend.product.scan;

import com.canmakan.backend.family.service.FamilyAuthorizationService;
import com.canmakan.backend.integration.BarcodeValidationClient;
import com.canmakan.backend.product.assessment.AssessmentOrchestrator;
import com.canmakan.backend.product.assessment.dto.AssessmentRequest;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthUserChecker;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product barcode scan APIs: validate, assess, and profile scan history.
 *
 * <p>{@code POST /api/scan/validate} is the is-food check (OFF + EAN-Search).
 * {@code POST /api/scan/assess} runs the tiered assessment for a dietary profile.
 * {@code GET /api/scan/history/{profileId}} returns saved scans for an authorized profile.
 *
 * @author Khai
 * @author Amelia
 * @author XieHuayuan
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/scan")
public class ScanController {

    private final BarcodeValidationClient validationClient;
    private final AssessmentOrchestrator orchestrator;
    private final ScanHistoryService scanHistoryService;
    private final FamilyAuthorizationService familyAuthorization;
    private final ScanFeedbackService scanFeedbackService;

    /**
     * Assess a barcode against a dietary profile, persist, and return the verdict.
     *
     * <p>{@code profileId} is required in the body (seeded profiles start at {@code 1}).
     * Caller identity comes from the JWT principal.
     */
    @PostMapping("/assess")
    public ResponseEntity<?> scan(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @Valid @RequestBody AssessmentRequest request) {
        Long userId = AuthUserChecker.requireUserId(userDetails);
        return ResponseEntity.ok(orchestrator.assess(userId, request));
    }

    /**
     * Standalone food-item check (OFF, then EAN-Search). Not used by the
     * combined scan path.
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidationResponse> validateBarcode(@Valid @RequestBody ScanRequest request) {
        ValidationResponse response = validationClient.validateProduct(request.barcode());
        return ResponseEntity.ok(response);
    }

    /**
     * Scan history for a dietary profile, most recent first (UC4 personal history).
     * Requires JWT and profile ownership / family membership.
     */
    @GetMapping("/history/{profileId}")
    public List<ScanHistoryResponse> getScanHistoryForProfile(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long profileId) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        familyAuthorization.assertProfileAuthorizedForScan(userId, profileId);
        List<ScanHistoryResponse> response = scanHistoryService.getScanHistoryForProfile(profileId);
        log.info("GET /scan/history/{} → 200 count={}", profileId, response.size());
        return response;
    }

    /**
     * Records a thumbs up/down on a scan verdict (UC20 report incorrect
     * product info). {@code isPositive} is required; the elaboration comment
     * is optional and only expected alongside a thumbs down — a bare thumbs
     * down (or a thumbs up) still saves a row with a null comment.
     * Requires JWT and profile ownership / family membership over the scan.
     */
    @PostMapping("/{scanId}/feedback")
    public ResponseEntity<?> submitScanFeedback(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long scanId,
            @Valid @RequestBody ScanFeedbackRequest request) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        ScanFeedbackResponse response = scanFeedbackService.submitFeedback(
                userId, scanId, request.isPositive(), request.userComments());
        log.info("POST /scan/{}/feedback → 201 isPositive={}", scanId, request.isPositive());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
