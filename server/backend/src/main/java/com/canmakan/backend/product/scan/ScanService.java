package com.canmakan.backend.product.scan;

import com.canmakan.backend.product.verdict.Finding;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Saves the outcome of a scan into the {@code scans} table after the
 * {@link com.canmakan.backend.product.verdict.DietaryRuleEngine} produces a
 * {@link SafetyVerdict}.
 *
 * <p>Call {@link #record} from {@code ScanController} right after
 * {@code engine.assess(...)}, e.g.:
 * <pre>
 *   SafetyVerdict verdict = engine.assess(rules, product);
 *   scanService.record(userId, profileId, barcode, verdict);
 * </pre>
 *
 * @author XieHuayuan
 */
@Service
public class ScanService {

    private final ScanRepository scanRepository;
    private final ObjectMapper objectMapper;

    public ScanService(ScanRepository scanRepository, ObjectMapper objectMapper) {
        this.scanRepository = scanRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persist one scan result.
     *
     * @param userId    the scanning user (from the auth token)
     * @param profileId the active dietary profile the product was assessed against
     * @param barcode   the scanned product barcode (null for OCR-only / not-found)
     * @param verdict   the engine's result
     * @return the saved {@link Scan}
     */
    public Scan record(Long userId, Long profileId, String barcode, SafetyVerdict verdict) {
        Scan scan = new Scan();
        scan.setUserId(userId);
        scan.setProfileId(profileId);
        scan.setBarcode(barcode);
        scan.setVerdict(verdict.toScansVerdict());        // "SAFE" / "WARNING" / "UNSAFE"
        scan.setAiExplanation(verdict.explanation());
        scan.setFindingsJson(toJson(verdict.findings()));
        scan.setScannedAt(LocalDateTime.now());
        return scanRepository.save(scan);
    }

    private String toJson(List<Finding> findings) {
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
