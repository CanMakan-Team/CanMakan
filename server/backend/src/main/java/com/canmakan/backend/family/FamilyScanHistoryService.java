package com.canmakan.backend.family;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.repository.DietaryRestrictionRepository;
import com.canmakan.backend.family.dto.FamilyScanRecordResponse;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.product.model.ScanProduct;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanRepository;
import com.canmakan.backend.product.verdict.Finding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read side of the Family Admin Portal's "Family Scan History" screen:
 * composes saved {@link Scan} rows across every profile in the caller's
 * family into a single, most-recent-first list, so the dashboard can fetch
 * a family's whole scan history from one endpoint instead of one request per
 * member (see {@link ScanRepository#findByProfileIdInWithProductOrderByScannedAtDesc}
 * for how that stays a single query too).
 *
 * @author Amelia
 */
@Service
@RequiredArgsConstructor
public class FamilyScanHistoryService {

    private static final String NOT_IN_FAMILY_MESSAGE =
        "You are not a member of a family circle.";
    private static final String INCOMPLETE_DATA_CODE = "INCOMPLETE_DATA";
    private static final String UNRESOLVED_CODE = "UNRESOLVED";
    private static final String NO_SUPPLIED_MATCH = "No supplied match";
    private static final String NONE_FLAGGED = "None flagged";

    // scannedAt stays a LocalDateTime on the Scan entity; truncating to seconds
    // before formatting keeps the shape sent to the client deterministic (see
    // ScanHistoryService#formatScannedAt for the same reasoning on the mobile side).
    private static final DateTimeFormatter SCANNED_AT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final FamilyMemberRepository familyMemberRepository;
    private final DietaryProfileRepository dietaryProfileRepository;
    private final DietaryRestrictionRepository dietaryRestrictionRepository;
    private final ScanRepository scanRepository;
    private final ObjectMapper objectMapper;

    /**
     * Scan history for every profile in the caller's family, most recent first.
     * @param userId the authenticated user's id
     * @return the family's scan history, or an empty list if the family has no profiles yet
     */
    @Transactional(readOnly = true)
    public List<FamilyScanRecordResponse> getFamilyScanHistory(long userId) {
        FamilyMember membership = familyMemberRepository.findMembershipByUserId(userId)
            .orElseThrow(() -> new FamilyNotFoundException(NOT_IN_FAMILY_MESSAGE));

        List<DietaryProfile> profiles =
            dietaryProfileRepository.findProfilesByFamilyId(membership.getFamilyId());
        if (profiles.isEmpty()) {
            return List.of();
        }

        Map<Long, DietaryProfile> profileById = new HashMap<>();
        for (DietaryProfile profile : profiles) {
            profileById.put(profile.getId(), profile);
        }

        List<Scan> scans = scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(
            new ArrayList<>(profileById.keySet()));

        List<FamilyScanRecordResponse> rows = new ArrayList<>(scans.size());
        for (Scan scan : scans) {
            // A scan's profile may have left the family (deactivated) since the
            // profile list above was loaded; skip rather than surface a broken row.
            DietaryProfile profile = profileById.get(scan.getProfileId());
            if (profile != null) {
                rows.add(toResponse(scan, profile));
            }
        }
        return rows;
    }

    /**
     * Converts a Scan and its evaluated profile into a FamilyScanRecordResponse.
     * @param scan the Scan object to convert
     * @param profile the dietary profile the scan was evaluated against
     * @return the FamilyScanRecordResponse object
     */
    private FamilyScanRecordResponse toResponse(Scan scan, DietaryProfile profile) {
        ScanProduct product = scan.getProduct();
        List<Finding> findings = parseFindings(scan.getFindingsJson());
        Finding notable = findings.isEmpty() ? null : findings.get(0);

        return new FamilyScanRecordResponse(
            scan.getId(),
            product != null ? product.getProductName() : "Unknown product",
            product != null && product.getBrand() != null ? product.getBrand() : "",
            resolveMemberId(profile),
            profile.getProfileName(),
            mapVerdict(scan.getVerdict()),
            notable != null && notable.ingredientName() != null
                ? notable.ingredientName() : NONE_FLAGGED,
            notable != null && notable.restrictionCode() != null
                ? notable.restrictionCode() : NO_SUPPLIED_MATCH,
            notable != null ? resolveRestrictionDisplayName(notable.restrictionCode()) : NO_SUPPLIED_MATCH,
            scan.getAiExplanation() != null ? scan.getAiExplanation() : "",
            resolveDataCompleteness(product, findings),
            product != null
                ? "Catalog product record and backend assessment"
                : "Product not found in catalog",
            formatScannedAt(scan.getScannedAt()),
            null
        );
    }

    // Registered members use their user id; dependants (no linked user) use their profile id.
    // Mirrors the convention already used for FamilyMemberRosterDto rows.
    private long resolveMemberId(DietaryProfile profile) {
        return profile.getLinkedUser() != null ? profile.getLinkedUser().getId() : profile.getId();
    }

    // The scans.verdict column stores SAFE / WARNING / UNSAFE; "Avoid" is the
    // user-facing label for UNSAFE (see SafetyVerdict.Level).
    private String mapVerdict(String rawVerdict) {
        return "UNSAFE".equals(rawVerdict) ? "AVOID" : rawVerdict;
    }

    // No product record at all means the assessment could not be completed;
    // an INCOMPLETE_DATA/UNRESOLVED finding means the product was found but
    // its ingredient data was only partially usable.
    private String resolveDataCompleteness(ScanProduct product, List<Finding> findings) {
        if (product == null) {
            return "PRODUCT_NOT_FOUND";
        }
        boolean incomplete = findings.stream().anyMatch(finding ->
            INCOMPLETE_DATA_CODE.equals(finding.restrictionCode())
                || UNRESOLVED_CODE.equals(finding.restrictionCode()));
        return incomplete ? "PARTIAL" : "COMPLETE";
    }

    private String resolveRestrictionDisplayName(String restrictionCode) {
        if (restrictionCode == null) {
            return NO_SUPPLIED_MATCH;
        }
        return dietaryRestrictionRepository.findByCodeIgnoreCase(restrictionCode)
            .map(DietaryRestriction::getDisplayName)
            .orElse(restrictionCode);
    }

    private String formatScannedAt(LocalDateTime scannedAt) {
        return scannedAt != null
            ? scannedAt.truncatedTo(ChronoUnit.SECONDS).format(SCANNED_AT_FORMATTER)
            : null;
    }

    // Stopgap translation: findings_json is written by ScanService as a JSON
    // array of Finding(restrictionCode, ingredientName, reason) objects (see
    // ScanHistoryService#parseFindings for the same parsing on the mobile side).
    private List<Finding> parseFindings(String findingsJson) {
        if (findingsJson == null || findingsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(findingsJson, new TypeReference<List<Finding>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
