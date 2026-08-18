package com.canmakan.backend.product.recommendation.history;

import com.canmakan.backend.product.recommendation.catalog.CatalogProduct;
import com.canmakan.backend.product.recommendation.catalog.CatalogProductRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.canmakan.backend.product.scan.Scan;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC17: RecommendationHistoryService")
class RecommendationHistoryServiceTest {

    @Mock
    private RecommendationLogService recommendationLogService;
    @Mock
    private com.canmakan.backend.product.scan.ScanRepository scanRepository;
    @Mock
    private CatalogProductRepository catalogProductRepository;

    private RecommendationHistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = new RecommendationHistoryService(
                recommendationLogService,
                scanRepository,
                catalogProductRepository
        );
    }

    @Test
    void returnsEmptyHistoryWhenProfileHasNoLogs() {
        when(recommendationLogService.listHistoryForProfile(1L)).thenReturn(List.of());

        RecommendationHistoryResponse response = historyService.getHistoryForProfile(1L);

        assertEquals(1L, response.profileId());
        assertTrue(response.history().isEmpty());
    }

    @Test
    void groupsMultipleAlternativesUnderSameScan() {
        RecommendationLog first = log(
                1L,
                2L,
                "0038527591039",
                "9315090200706",
                "Ancient grain flakes",
                "Freedom Foods",
                new BigDecimal("0.9900"),
                "category_match",
                LocalDateTime.of(2026, 8, 3, 10, 5, 0)
        );
        RecommendationLog second = log(
                2L,
                2L,
                "0038527591039",
                "9315090200707",
                "Rice flakes",
                "Freedom Foods",
                new BigDecimal("0.9800"),
                "prior_safe_scan",
                LocalDateTime.of(2026, 8, 3, 10, 6, 0)
        );

        CatalogProduct source = catalogProduct("0038527591039", "Oatmeal Squares Original", "Quaker");
        Scan scan = scan(2L, "0038527591039", "UNSAFE", LocalDateTime.of(2026, 8, 3, 10, 4, 30));

        when(recommendationLogService.listHistoryForProfile(1L)).thenReturn(List.of(first, second));
        when(scanRepository.findAllById(Set.of(2L))).thenReturn(List.of(scan));
        when(catalogProductRepository.findAllById(Set.of("0038527591039"))).thenReturn(List.of(source));

        RecommendationHistoryResponse response = historyService.getHistoryForProfile(1L);

        assertEquals(1, response.history().size());
        RecommendationHistoryEntryDto entry = response.history().getFirst();
        assertEquals(2L, entry.scanId());
        assertEquals("0038527591039", entry.sourceBarcode());
        assertEquals("Oatmeal Squares Original", entry.sourceProductName());
        assertEquals("Quaker", entry.sourceBrand());
        assertEquals("UNSAFE", entry.sourceVerdict());
        assertEquals("2026-08-03T10:04:30", entry.recommendedAt());
        assertEquals(2, entry.alternatives().size());
        assertEquals("9315090200706", entry.alternatives().get(0).barcode());
        assertEquals(new BigDecimal("0.9900"), entry.alternatives().get(0).rankScore());
        assertEquals("9315090200707", entry.alternatives().get(1).barcode());
    }

    @Test
    void usesLogCreatedAtWhenScanIsMissing() {
        RecommendationLog orphan = log(
                3L,
                null,
                "9300698500181",
                "5400601063674",
                "Hazelnut spread",
                "Delhaize 365",
                new BigDecimal("0.9500"),
                "substitute_category",
                LocalDateTime.of(2026, 8, 5, 12, 0, 0)
        );

        when(recommendationLogService.listHistoryForProfile(1L)).thenReturn(List.of(orphan));
        when(catalogProductRepository.findAllById(Set.of("9300698500181"))).thenReturn(List.of());

        RecommendationHistoryResponse response = historyService.getHistoryForProfile(1L);

        RecommendationHistoryEntryDto entry = response.history().getFirst();
        assertEquals(null, entry.scanId());
        assertEquals(null, entry.sourceVerdict());
        assertEquals("2026-08-05T12:00:00", entry.recommendedAt());
        assertEquals("Unknown product", entry.sourceProductName());
    }

    @Test
    void dedupesDuplicateRecommendedBarcodesWithinSameScanSession() {
        RecommendationLog first = log(
                1L,
                2L,
                "9555064500016",
                "8888263533730",
                "Coconut Flour",
                "Redman",
                new BigDecimal("0.9900"),
                "substitute_category",
                LocalDateTime.of(2026, 8, 13, 10, 5, 0)
        );
        RecommendationLog duplicate = log(
                2L,
                2L,
                "9555064500016",
                "8888263533730",
                "Coconut Flour",
                "Redman",
                new BigDecimal("0.9800"),
                "ml_similarity",
                LocalDateTime.of(2026, 8, 13, 10, 6, 0)
        );

        CatalogProduct source = catalogProduct("9555064500016", "Superfine Wheat Flour", "Baker Choice");
        Scan scan = scan(2L, "9555064500016", "UNSAFE", LocalDateTime.of(2026, 8, 13, 10, 4, 30));

        when(recommendationLogService.listHistoryForProfile(1L)).thenReturn(List.of(first, duplicate));
        when(scanRepository.findAllById(Set.of(2L))).thenReturn(List.of(scan));
        when(catalogProductRepository.findAllById(Set.of("9555064500016"))).thenReturn(List.of(source));

        RecommendationHistoryResponse response = historyService.getHistoryForProfile(1L);

        assertEquals(1, response.history().size());
        assertEquals(1, response.history().getFirst().alternatives().size());
        assertEquals("8888263533730", response.history().getFirst().alternatives().getFirst().barcode());
        assertEquals(new BigDecimal("0.9900"), response.history().getFirst().alternatives().getFirst().rankScore());
    }

    private static RecommendationLog log(
            Long id,
            Long scanId,
            String sourceBarcode,
            String recommendedBarcode,
            String recommendedName,
            String recommendedBrand,
            BigDecimal rankScore,
            String matchReason,
            LocalDateTime createdAt) {
        RecommendationLog log = new RecommendationLog();
        log.setId(id);
        log.setProfileId(1L);
        log.setScanId(scanId);
        log.setSourceBarcode(sourceBarcode);
        log.setRecommendedBarcode(recommendedBarcode);
        log.setRecommendedName(recommendedName);
        log.setRecommendedBrand(recommendedBrand);
        log.setDiscoveryTier("TIER_A_CATALOG");
        log.setRankScore(rankScore);
        log.setMatchReason(matchReason);
        log.setShownToUser(true);
        log.setCreatedAt(createdAt);
        return log;
    }

    private static CatalogProduct catalogProduct(String barcode, String name, String brand) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName(name);
        product.setBrand(brand);
        return product;
    }

    private static Scan scan(Long id, String barcode, String verdict, LocalDateTime scannedAt) {
        Scan scan = new Scan();
        scan.setId(id);
        scan.setBarcode(barcode);
        scan.setVerdict(verdict);
        scan.setScannedAt(scannedAt);
        scan.setProfileId(1L);
        scan.setUserId(4L);
        return scan;
    }
}
