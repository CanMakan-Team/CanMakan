package com.canmakan.backend.product.scan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read side of "view scan verdicts": returns saved scan history for a dietary
 * profile, newest first. Composition of scan + product data happens behind
 * this single endpoint (in {@link ScanHistoryService}) so the client only
 * needs to call one place to render the history screen.
 *
 * @author XieHuayuan
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class ScanHistoryController {

    private final ScanHistoryService scanHistoryService;

    public ScanHistoryController(ScanHistoryService scanHistoryService) {
        this.scanHistoryService = scanHistoryService;
    }

    /** Scan history for a dietary profile, most recent first. */
    @GetMapping("/profiles/{profileId}/history")
    public List<ScanHistoryResponse> getScanHistoryForProfile(@PathVariable Long profileId) {
        List<ScanHistoryResponse> response = scanHistoryService.getScanHistoryForProfile(profileId);
        log.info("GET /profiles/{profileId}/history → 200");
        return response;
    }
}
