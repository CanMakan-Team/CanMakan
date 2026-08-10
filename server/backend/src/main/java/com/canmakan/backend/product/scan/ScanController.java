package com.canmakan.backend.product.scan;

import com.canmakan.backend.integration.BarcodeValidationClient;
import com.canmakan.backend.product.assessment.AssessmentOrchestrator;
import com.canmakan.backend.product.assessment.AssessmentRequest;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.AuthUserChecker;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product barcode scan APIs.
 *
 * <p>{@code POST /api/scan/validate} is the is-food check (OFF + EAN-Search).
 * {@code POST /api/scan/assess} runs the tiered assessment for a dietary profile.
 *
 * @author Khai
 * @author Amelia
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/scan")
public class ScanController {

    private final BarcodeValidationClient validationClient;
    private final AssessmentOrchestrator orchestrator;

    /**
     * Assess a barcode against a dietary profile, persist, and return the verdict.
     *
     * <p>{@code profileId} is required in the body (seeded profiles start at {@code 1}).
     * Caller identity comes from the JWT principal.
     */
    @PostMapping("/assess")
    public ResponseEntity<?> scan(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestBody AssessmentRequest request) {
        if (request == null || request.barcode() == null || request.barcode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Product Barcode is required"));
        }
        if (request.profileId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message",
                    "Profile ID is required"
            ));
        }
        Long userId = AuthUserChecker.requireUserId(userDetails);
        return ResponseEntity.ok(orchestrator.assess(userId, request));
    }

    /**
     * Standalone food-item check (OFF, then EAN-Search). Not used by the
     * combined scan path.
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidationResponse> validateBarcode(@RequestBody ScanRequest request) {
        ValidationResponse response = validationClient.validateProduct(request.barcode());
        return ResponseEntity.ok(response);
    }

}
