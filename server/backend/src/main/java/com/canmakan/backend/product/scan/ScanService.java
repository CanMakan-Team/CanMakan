package com.canmakan.backend.product.scan;

import com.canmakan.backend.product.model.ScanProduct;
import com.canmakan.backend.product.model.ScanProductRepository;
import com.canmakan.backend.product.verdict.Finding;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Saves the outcome of a scan into the {@code scans} table after the
 * {@link com.canmakan.backend.product.verdict.DietaryRuleEngine} produces a
 * {@link SafetyVerdict}.
 *
 * <p>When a barcode comes from Open Food Facts and is not yet in local
 * {@code products}, a catalog stub row is inserted first so
 * {@code fk_scans_product} is satisfied.
 *
 * @author XieHuayuan
 * @author Amelia
 */
@Service
@RequiredArgsConstructor
public class ScanService {

    private final ScanRepository scanRepository;
    private final ScanProductRepository scanProductRepository;
    private final ObjectMapper objectMapper;

    /**
     * Persist one scan result, ensuring a matching {@code products} row exists.
     *
     * @param userId      the scanning user (from the auth token)
     * @param profileId   the active dietary profile the product was assessed against
     * @param barcode     the scanned product barcode (null for OCR-only / not-found)
     * @param verdict     the engine's result
     * @param productName display name from OFF (used when inserting a catalog stub)
     * @return the saved {@link Scan}
     */
    @Transactional
    public Scan saveScan(
            Long userId,
            Long profileId,
            String barcode,
            SafetyVerdict verdict,
            String productName
    ) {
        ensureProductRow(barcode, productName);

        Scan scan = new Scan();
        scan.setUserId(userId);
        scan.setProfileId(profileId);
        scan.setBarcode(blankToNull(barcode));
        scan.setVerdict(verdict.toScansVerdict());
        scan.setAiExplanation(verdict.explanation());
        scan.setFindingsJson(toJson(verdict.findings()));
        scan.setScannedAt(LocalDateTime.now(ZoneOffset.UTC));
        return scanRepository.save(scan);
    }

    /**
     * Inserts a minimal {@code products} row when the barcode is new.
     * Required because {@code scans.barcode} references {@code products.barcode}.
     */
    void ensureProductRow(String barcode, String productName) {
        String key = blankToNull(barcode);
        if (key == null) {
            return;
        }
        if (scanProductRepository.existsById(key)) {
            return;
        }
        ScanProduct product = new ScanProduct();
        product.setBarcode(key);
        product.setProductName(displayName(productName));
        scanProductRepository.save(product);
    }

    // --- Helper methods ---

    // Format the product name
    private static String displayName(String productName) {
        if (productName == null || productName.isBlank()) {
            return "Unknown product";
        }
        return productName.trim();
    }

    // Convert a blank string to null
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Convert the findings to JSON
    private String toJson(List<Finding> findings) {
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
