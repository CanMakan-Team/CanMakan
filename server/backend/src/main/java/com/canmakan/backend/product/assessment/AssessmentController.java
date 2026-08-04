package com.canmakan.backend.product.assessment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the full assess-and-record flow (in addition to the teammate's
 * {@code /api/scan/validate} barcode check).
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
@RestController
@RequestMapping("/api/scan")
public class AssessmentController {

    private final AssessmentOrchestrator orchestrator;

    public AssessmentController(AssessmentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Scan a product for a profile, run the tiered assessment, persist and return it.
     */
    @PostMapping("/assess")
    public ResponseEntity<AssessmentResponse> assess(@RequestBody AssessmentRequest request) {
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Assessment requires a verified authenticated principal."
        );
    }
}
