package com.canmakan.backend.product.assessment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exposes the full assess-and-record flow in addition to the barcode
 * validation endpoint.
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
     * Blocks assessment until a verified authenticated principal is available.
     *
     * @param request the assessment request
     * @return the assessment response when authentication is implemented
     */
    @PostMapping("/assess")
    public ResponseEntity<AssessmentResponse> assess(
            @RequestBody AssessmentRequest request) {
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Assessment requires a verified authenticated principal."
        );
    }
}