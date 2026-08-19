package com.canmakan.backend.family.service;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.family.dto.FamilyScanHistoryDto;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.product.model.ScanProduct;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the family scan history (web history/dashboard, UC4 AC10): loads recent scans across a
 * family's profiles and maps each one onto the wire DTO, including SAFE/WARNING/UNSAFE verdict
 * normalization. Split out of {@link com.canmakan.backend.family.FamilyService} to keep that
 * class's dependency count within Sonar's class-coupling limit.
 */
@Service
@RequiredArgsConstructor
public class FamilyScanHistoryService {

    private static final String VERDICT_WARNING = "WARNING";
    private static final DateTimeFormatter SCAN_AT_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DietaryProfileRepository dietaryProfileRepository;
    private final ScanRepository scanRepository;
    private final FamilyAuthorizationService familyAuthorization;

    /**
     * Lists recent scans for all profiles in the caller's family (web history/dashboard).
     * PRIMARY_ADMIN only (UC4 AC10).
     */
    @Transactional(readOnly = true)
    public List<FamilyScanHistoryDto> listFamilyScans(long userId) {
        FamilyMember membership = familyAuthorization.requirePrimaryAdmin(userId);
        Long familyId = membership.getFamilyId();
        List<DietaryProfile> profiles =
            dietaryProfileRepository.findAllProfilesByFamilyId(familyId);
        if (profiles.isEmpty()) {
            return List.of();
        }
        Map<Long, DietaryProfile> profilesById = new HashMap<>();
        for (DietaryProfile profile : profiles) {
            profilesById.put(profile.getId(), profile);
        }
        List<Scan> scans = scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(
            profilesById.keySet());
        List<FamilyScanHistoryDto> rows = new ArrayList<>();
        for (Scan scan : scans) {
            DietaryProfile profile = profilesById.get(scan.getProfileId());
            rows.add(toFamilyScanHistoryRow(scan, profile));
        }
        return rows;
    }

    private FamilyScanHistoryDto toFamilyScanHistoryRow(Scan scan, DietaryProfile profile) {
        ScanProduct product = scan.getProduct();
        String productName = resolveProductName(product, scan.getBarcode());
        String brand = product != null && product.getBrand() != null ? product.getBrand() : "";
        String profileName = profile != null && profile.getProfileName() != null
            ? profile.getProfileName()
            : "Unknown profile";
        long memberId = resolveScanMemberId(profile);
        String explanation = scan.getAiExplanation() == null ? "" : scan.getAiExplanation();
        return new FamilyScanHistoryDto(
            scan.getId() == null ? 0L : scan.getId(),
            productName,
            brand,
            memberId,
            profileName,
            mapWebVerdict(scan.getVerdict()),
            "",
            "",
            "",
            explanation,
            product == null ? "PRODUCT_NOT_FOUND" : "COMPLETE",
            "Open Food Facts / assessment",
            formatScanAt(scan.getScannedAt()),
            null
        );
    }

    private static String resolveProductName(ScanProduct product, String barcode) {
        if (product != null && product.getProductName() != null) {
            return product.getProductName();
        }
        return barcode != null ? barcode : "Unknown product";
    }

    private static long resolveScanMemberId(DietaryProfile profile) {
        if (profile == null) {
            return 0L;
        }
        if (profile.getLinkedUser() != null && profile.getLinkedUser().getId() != null) {
            return profile.getLinkedUser().getId();
        }
        return profile.getId() != null ? profile.getId() : 0L;
    }

    /**
     * Scan verdicts on the wire are {@code SAFE} | {@code WARNING} | {@code UNSAFE} only.
     */
    private static String mapWebVerdict(String verdict) {
        if (verdict == null || verdict.isBlank()) {
            return VERDICT_WARNING;
        }
        String normalized = verdict.trim().toUpperCase(Locale.ROOT);
        if ("SAFE".equals(normalized)
                || VERDICT_WARNING.equals(normalized)
                || "UNSAFE".equals(normalized)) {
            return normalized;
        }
        // Legacy web label from older mocks / clients.
        if ("AVOID".equals(normalized)) {
            return "UNSAFE";
        }
        return VERDICT_WARNING;
    }

    private static String formatScanAt(LocalDateTime scannedAt) {
        if (scannedAt == null) {
            return Instant.now().atOffset(ZoneOffset.UTC).format(SCAN_AT_FORMAT);
        }
        return scannedAt.format(SCAN_AT_FORMAT);
    }
}
