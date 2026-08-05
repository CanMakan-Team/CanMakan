package com.canmakan.backend.product.scan;

import com.canmakan.backend.integration.BarcodeValidationClient;
import com.canmakan.backend.product.assessment.AssessmentOrchestrator;
import com.canmakan.backend.product.assessment.AssessmentRequest;
import com.canmakan.backend.product.assessment.AssessmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product barcode scan APIs.
 *
 * <p>{@code POST /api/scan} is the mobile path: one Open Food Facts fetch,
 * then tiered assessment and persist. {@code POST /api/scan/validate}
 * remains a standalone is-food check (OFF + EAN-Search fallback).
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
     * Combined scan: fetch product once from Open Food Facts, assess for the
     * profile, persist, and return the verdict. Does not call {@code /validate}.
     *
     * <p>The user id currently comes from an {@code X-User-Id} header; swap this for
     * the authenticated principal once auth lands.
     */
    @PostMapping("/assess")
    public ResponseEntity<AssessmentResponse> scan(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody AssessmentRequest request) {
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
