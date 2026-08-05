package com.canmakan.backend.product.scan;

import com.canmakan.backend.product.model.ScanProduct;
import com.canmakan.backend.product.verdict.Finding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Read side of "view scan verdicts": composes each saved {@link Scan} together
 * with its matching {@link ScanProduct} into a single {@link
 * ScanHistoryResponse}, so the Android app can fetch a profile's full scan
 * history from one endpoint instead of joining scan and product data itself.
 *
 * @author XieHuayuan
 */
@Service
public class ScanHistoryService {

    private static final String PLACEHOLDER_PRODUCT_NAME = "Unknown product";

    private final ScanRepository scanRepository;
    private final ObjectMapper objectMapper;

    public ScanHistoryService(ScanRepository scanRepository, ObjectMapper objectMapper) {
        this.scanRepository = scanRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Scan history for a dietary profile, most recent first, with the product
     * for each scan already loaded (see {@link
     * ScanRepository#findByProfileIdWithProductOrderByScannedAtDesc} for how
     * that's done in a single query).
     */
    public List<ScanHistoryResponse> getScanHistoryForProfile(Long profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("profileId must not be null");
        }

        return scanRepository.findByProfileIdWithProductOrderByScannedAtDesc(profileId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ScanHistoryResponse toResponse(Scan scan) {
        return new ScanHistoryResponse(
                scan.getId(),
                scan.getProfileId(),
                scan.getBarcode(),
                toProductDto(scan),
                scan.getScannedAt() != null ? scan.getScannedAt().toString() : null,
                scan.getVerdict(),
                toFindingsDto(scan.getFindingsJson()),
                scan.getAiExplanation()
        );
    }

    // The scans.barcode FK can be null (OCR-only scans), or point at a
    // products row that has since been deleted. The Android app's Product
    // field is non-nullable, so a placeholder is returned rather than null in
    // that case, instead of forcing every call site on the client to
    // null-check the product.
    private ScanHistoryResponse.ProductDto toProductDto(Scan scan) {
        ScanProduct product = scan.getProduct();
        if (product != null) {
            return new ScanHistoryResponse.ProductDto(
                    product.getProductName(),
                    product.getBrand(),
                    product.getBarcode()
            );
        }
        return new ScanHistoryResponse.ProductDto(
                PLACEHOLDER_PRODUCT_NAME,
                "",
                scan.getBarcode() != null ? scan.getBarcode() : ""
        );
    }

    // Stopgap translation: findings_json is currently written by ScanService
    // as a JSON array of Finding(restrictionCode, ingredientName, reason)
    // objects, not the {matched_rules, allergens_found} shape the Android
    // FindingsJson class expects. Finding has no field marking a finding as an
    // allergen specifically, so allergensFound is left empty here rather than
    // guessed at. The real fix is on the write side (give Finding a type/
    // discriminator and have ScanService populate both lists at scan time) —
    // tracked as a follow-up, not solved in this read-side mapping.
    private ScanHistoryResponse.FindingsDto toFindingsDto(String findingsJson) {
        List<Finding> findings = parseFindings(findingsJson);

        List<String> matchedRules = findings.stream()
                .map(Finding::restrictionCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // TODO: Finding has no allergen discriminator yet — see follow-up above.
        List<String> allergensFound = Collections.emptyList();

        return new ScanHistoryResponse.FindingsDto(matchedRules, allergensFound);
    }

    private List<Finding> parseFindings(String findingsJson) {
        if (findingsJson == null || findingsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(findingsJson, new TypeReference<List<Finding>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
