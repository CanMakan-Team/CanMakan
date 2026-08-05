// package com.canmakan.backend.product.assessment;

// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RequestHeader;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RestController;

// migrated to ScanController

// /**
//  * Exposes the full assess-and-record flow (in addition to the teammate's
//  * {@code /api/scan/validate} barcode check).
//  *
//  * @author XieHuayuan
//  */
// @RestController
// @RequestMapping("/api/scan")
// public class AssessmentController {

//     private final AssessmentOrchestrator orchestrator;

//     public AssessmentController(AssessmentOrchestrator orchestrator) {
//         this.orchestrator = orchestrator;
//     }

//     /**
//      * Scan a product for a profile, run the tiered assessment, persist and return it.
//      *
//      * <p>The user id currently comes from an {@code X-User-Id} header; swap this for
//      * the authenticated principal in the {@code SecurityContext} once the auth
//      * module lands.
//      */
//     @PostMapping("/assess")
//     public ResponseEntity<AssessmentResponse> assess(
//             @RequestHeader(value = "X-User-Id", required = false) Long userId,
//             @RequestBody AssessmentRequest request) {
//         return ResponseEntity.ok(orchestrator.assess(userId, request));
//     }
// }
