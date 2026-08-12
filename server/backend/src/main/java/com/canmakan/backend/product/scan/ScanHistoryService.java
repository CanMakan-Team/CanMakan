package com.canmakan.backend.product.scan;

import com.canmakan.backend.product.model.ScanProduct;
import com.canmakan.backend.product.verdict.Finding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read side of "view scan verdicts": composes each saved {@link Scan} together
 * with its matching {@link ScanProduct} into a single {@link
 * ScanHistoryResponse}, so the Android app can fetch a profile's full scan
 * history from one endpoint instead of joining scan and product data itself.
 *
 * @author XieHuayuan
 * @author Amelia
 */
@Service
@RequiredArgsConstructor
public class ScanHistoryService {

    private static final String PLACEHOLDER_PRODUCT_NAME = "Unknown product";

    // scannedAt stays a LocalDateTime on the Scan entity, but LocalDateTime#toString()
    // omits the fractional-second part whenever it is zero, so the exact string shape
    // sent to Android would vary from row to row (e.g. "...05" vs "...05.5" vs
    // "...05.500000"). Truncating to seconds and formatting with a fixed pattern keeps
    // the JSON value deterministic and trivially parseable with
    // LocalDateTime.parse(...) (default ISO_LOCAL_DATE_TIME parser) on the Android side.
    private static final DateTimeFormatter SCANNED_AT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ScanRepository scanRepository;
    private final ObjectMapper objectMapper;

    /**
     * Scan history for a dietary profile, most recent first, with the product
     * for each scan already loaded (see {@link
     * ScanRepository#findByProfileIdWithProductOrderByScannedAtDesc} for how
     * that's done in a single query).
     * @param profileId the profile ID
     * @return the scan history for the profile
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

    /**
     * Converts a Scan object to a ScanHistoryResponse object.
     * @param scan the Scan object to convert
     * @return the ScanHistoryResponse object
     */
    private ScanHistoryResponse toResponse(Scan scan) {
        return new ScanHistoryResponse(
                scan.getId(),
                scan.getProfileId(),
                scan.getBarcode(),
                toProductDto(scan),
                formatScannedAt(scan.getScannedAt()),
                scan.getVerdict(),
                toFindingsDto(scan.getFindingsJson()),
                scan.getAiExplanation()
        );
    }

    /**
     * Formats a scan's timestamp as a fixed-shape ISO-8601 string ("yyyy-MM-ddTHH:mm:ss").
     * Truncating to seconds before formatting keeps the output deterministic across rows,
     * unlike {@link LocalDateTime#toString()} which drops the fractional-second part only
     * when it happens to be zero.
     * @param scannedAt the entity's LocalDateTime, or null
     * @return the formatted timestamp, or null if scannedAt was null
     */
    private String formatScannedAt(LocalDateTime scannedAt) {
        return scannedAt != null
                ? scannedAt.truncatedTo(ChronoUnit.SECONDS).format(SCANNED_AT_FORMATTER)
                : null;
    }

    // The scans.barcode FK can be null (OCR-only scans), or point at a
    // products row that has since been deleted. The Android app's Product
    // field is non-nullable, so a placeholder is returned rather than null in
    // that case, instead of forcing every call site on the client to
    // null-check the product.
    /**
     * Converts a Scan object to a ProductDto object.
     * @param scan the Scan object to convert
     * @return the ProductDto object
     */
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

    // Stopgap translation: findings_json may be either
    // 1) Finding[] written by ScanService (live engine path), or
    // 2) legacy/seed {matched_rules, allergens_found} objects.
    // Android FindingsJson expects shape (2); map both into FindingsDto.
    /**
     * Converts a String to a FindingsDto object.
     * @param findingsJson the String to convert
     * @return the FindingsDto object
     */
    private ScanHistoryResponse.FindingsDto toFindingsDto(String findingsJson) {
        if (findingsJson == null || findingsJson.isBlank()) {
            return new ScanHistoryResponse.FindingsDto(List.of(), List.of());
        }

        try {
            JsonNode root = objectMapper.readTree(findingsJson);
            // Hibernate JSON-on-String can double-encode; unwrap a JSON string node.
            if (root != null && root.isTextual()) {
                root = objectMapper.readTree(root.asText());
            }
            if (root == null || root.isNull()) {
                return new ScanHistoryResponse.FindingsDto(List.of(), List.of());
            }

            if (root.isObject()) {
                return findingsDtoFromLegacyObject(root);
            }
            if (root.isArray()) {
                return findingsDtoFromFindingArray(root);
            }
            return new ScanHistoryResponse.FindingsDto(List.of(), List.of());
        } catch (JsonProcessingException e) {
            return new ScanHistoryResponse.FindingsDto(List.of(), List.of());
        }
    }

    private ScanHistoryResponse.FindingsDto findingsDtoFromLegacyObject(JsonNode root) {
        List<String> matchedRules = new ArrayList<>();
        List<String> allergensFound = new ArrayList<>();

        JsonNode rulesNode = root.get("matched_rules");
        if (rulesNode != null && rulesNode.isArray()) {
            for (JsonNode rule : rulesNode) {
                if (rule != null && rule.isTextual()) {
                    String value = rule.asText().trim();
                    if (!value.isEmpty() && !matchedRules.contains(value)) {
                        matchedRules.add(value);
                    }
                }
            }
        }

        JsonNode allergensNode = root.get("allergens_found");
        if (allergensNode != null && allergensNode.isArray()) {
            for (JsonNode allergen : allergensNode) {
                if (allergen != null && allergen.isTextual()) {
                    addAllergenIfUseful(allergensFound, allergen.asText());
                }
            }
        }

        return new ScanHistoryResponse.FindingsDto(matchedRules, allergensFound);
    }

    private ScanHistoryResponse.FindingsDto findingsDtoFromFindingArray(JsonNode root)
            throws JsonProcessingException {
        List<Finding> findings = objectMapper.convertValue(root, new TypeReference<List<Finding>>() {
        });

        List<String> matchedRules = new ArrayList<>();
        List<String> allergensFound = new ArrayList<>();
        for (Finding finding : findings) {
            if (finding == null) {
                continue;
            }
            String restrictionCode = finding.restrictionCode();
            if (restrictionCode != null && !restrictionCode.isBlank()
                    && !matchedRules.contains(restrictionCode)) {
                matchedRules.add(restrictionCode);
            }
            addAllergenIfUseful(allergensFound, finding.ingredientName());
        }

        return new ScanHistoryResponse.FindingsDto(matchedRules, allergensFound);
    }

    /** Skips blank names and Finding subject sentinels (unknown / label / nutrition). */
    private static void addAllergenIfUseful(List<String> allergensFound, String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return;
        }
        String trimmed = ingredientName.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (Finding.SUBJECT_UNKNOWN.equals(lower)
                || Finding.SUBJECT_LABEL.equals(lower)
                || Finding.SUBJECT_NUTRITION.equals(lower)) {
            return;
        }
        if (!allergensFound.contains(trimmed)) {
            allergensFound.add(trimmed);
        }
    }
}
