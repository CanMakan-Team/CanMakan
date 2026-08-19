package com.canmakan.backend.product.scan;

import com.canmakan.backend.product.model.ScanProduct;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanHistoryService findings_json mapping")
class ScanHistoryServiceTest {

    @Mock
    private ScanRepository scanRepository;

    private ScanHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ScanHistoryService(scanRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("legacy seed object shape maps matched_rules and allergens_found")
    void mapsLegacyMatchedRulesObject() {
        Scan scan = baseScan();
        scan.setVerdict("UNSAFE");
        scan.setAiExplanation("Contains wheat flour which violates the gluten-free constraint.");
        scan.setFindingsJson(
                "{\"matched_rules\": [\"GLUTEN_ALLERGY\"], \"allergens_found\": [\"Wheat Flour\"]}");
        when(scanRepository.findByProfileIdWithProductOrderByScannedAtDesc(1L))
                .thenReturn(List.of(scan));

        ScanHistoryResponse response = service.getScanHistoryForProfile(1L).getFirst();

        assertThat(response.findingsJson().matchedRules()).containsExactly("GLUTEN_ALLERGY");
        assertThat(response.findingsJson().allergensFound()).containsExactly("Wheat Flour");
    }

    @Test
    @DisplayName("Finding[] shape from ScanService maps restriction codes and ingredients")
    void mapsFindingArray() {
        Scan scan = baseScan();
        scan.setVerdict("UNSAFE");
        scan.setAiExplanation("Contains milk");
        scan.setFindingsJson(
                "[{\"restrictionCode\":\"DAIRY\",\"ingredientName\":\"Skimmed milk powder\","
                        + "\"reason\":\"Contains milk\"}]");
        when(scanRepository.findByProfileIdWithProductOrderByScannedAtDesc(1L))
                .thenReturn(List.of(scan));

        ScanHistoryResponse response = service.getScanHistoryForProfile(1L).getFirst();

        assertThat(response.findingsJson().matchedRules()).containsExactly("DAIRY");
        assertThat(response.findingsJson().allergensFound()).containsExactly("Skimmed milk powder");
    }

    @Test
    @DisplayName("Finding subject sentinel unknown is not returned as an allergen")
    void skipsUnknownAllergenSentinel() {
        Scan scan = baseScan();
        scan.setVerdict("WARNING");
        scan.setAiExplanation("No reliable ingredient data for this product.");
        scan.setFindingsJson(
                "[{\"restrictionCode\":\"INCOMPLETE_DATA\",\"ingredientName\":\"unknown\","
                        + "\"reason\":\"No reliable ingredient data for this product.\"}]");
        when(scanRepository.findByProfileIdWithProductOrderByScannedAtDesc(1L))
                .thenReturn(List.of(scan));

        ScanHistoryResponse response = service.getScanHistoryForProfile(1L).getFirst();

        assertThat(response.findingsJson().matchedRules()).containsExactly("INCOMPLETE_DATA");
        assertThat(response.findingsJson().allergensFound()).isEmpty();
    }

    @Test
    @DisplayName("UNRESOLVED and CROSS_CONTAMINATION findings do not leak into allergens_found")
    void excludesDataQualityFindingsFromAllergens() {
        Scan scan = baseScan();
        scan.setVerdict("UNSAFE");
        scan.setAiExplanation("Contains gluten");
        // A real allergen match plus the grouped UNRESOLVED caution (whose ingredientName is a
        // comma-joined list of unverified items) and a cross-contamination finding. Only the real
        // allergen match may appear in the Allergen list.
        scan.setFindingsJson(
                "[{\"restrictionCode\":\"GLUTEN\",\"ingredientName\":\"Corn Semolina\","
                        + "\"reason\":\"Corn Semolina matches the GLUTEN restriction.\"},"
                        + "{\"restrictionCode\":\"UNRESOLVED\","
                        + "\"ingredientName\":\"Sodium Chloride, Reduced Iron, Calcium Carbonate\","
                        + "\"reason\":\"Treat these ingredients with caution: Sodium Chloride, "
                        + "Reduced Iron, Calcium Carbonate.\"},"
                        + "{\"restrictionCode\":\"CROSS_CONTAMINATION\",\"ingredientName\":\"GLUTEN\","
                        + "\"reason\":\"Possible cross-contamination involving GLUTEN.\"}]");
        when(scanRepository.findByProfileIdWithProductOrderByScannedAtDesc(1L))
                .thenReturn(List.of(scan));

        ScanHistoryResponse response = service.getScanHistoryForProfile(1L).getFirst();

        assertThat(response.findingsJson().allergensFound()).containsExactly("Corn Semolina");
    }

    @Test
    @DisplayName("double-encoded JSON string still maps Finding[]")
    void unwrapsDoubleEncodedJsonString() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String inner = "[{\"restrictionCode\":\"PEANUT\",\"ingredientName\":\"Peanuts\","
                + "\"reason\":\"Contains peanuts\"}]";
        String wrapped = mapper.writeValueAsString(inner);

        Scan scan = baseScan();
        scan.setVerdict("UNSAFE");
        scan.setFindingsJson(wrapped);
        when(scanRepository.findByProfileIdWithProductOrderByScannedAtDesc(1L))
                .thenReturn(List.of(scan));

        ScanHistoryResponse response = service.getScanHistoryForProfile(1L).getFirst();

        assertThat(response.findingsJson().matchedRules()).containsExactly("PEANUT");
        assertThat(response.findingsJson().allergensFound()).containsExactly("Peanuts");
    }

    private static Scan baseScan() {
        ScanProduct product = new ScanProduct();
        product.setBarcode("123");
        product.setProductName("Test product");
        product.setBrand("Brand");

        Scan scan = new Scan();
        scan.setId(10L);
        scan.setUserId(1L);
        scan.setProfileId(1L);
        scan.setBarcode("123");
        scan.setProduct(product);
        scan.setScannedAt(LocalDateTime.of(2026, 1, 1, 12, 0, 0));
        return scan;
    }
}
