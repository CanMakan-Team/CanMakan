package com.canmakan.backend.product.scan;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read side of "view scan verdicts": returns saved scan history for a user or a
 * dietary profile, newest first.
 *
 * @author XieHuayuan
 */
@RestController
@RequestMapping("/api/scan/history")
public class ScanHistoryController {

    private final ScanRepository scanRepository;

    public ScanHistoryController(ScanRepository scanRepository) {
        this.scanRepository = scanRepository;
    }

    /** Scan history for a user, most recent first. */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ScanHistoryResponse>> byUser(@PathVariable Long userId) {
        return ResponseEntity.ok(
                toResponses(scanRepository.findByUserIdOrderByScannedAtDesc(userId)));
    }

    /** Scan history for a dietary profile, most recent first. */
    @GetMapping("/profile/{profileId}")
    public ResponseEntity<List<ScanHistoryResponse>> byProfile(@PathVariable Long profileId) {
        return ResponseEntity.ok(
                toResponses(scanRepository.findByProfileIdOrderByScannedAtDesc(profileId)));
    }

    private List<ScanHistoryResponse> toResponses(List<Scan> scans) {
        return scans.stream()
                .map(scan -> new ScanHistoryResponse(
                        scan.getId(),
                        scan.getBarcode(),
                        scan.getVerdict(),
                        scan.getAiExplanation(),
                        scan.getScannedAt()))
                .toList();
    }
}
