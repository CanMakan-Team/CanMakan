package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.dietaryprofile.RestrictionRuleLoader;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanRepository;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.RestrictionSeverity;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC5: RecommendationService")
class RecommendationServiceTest {

    @Mock private RestrictionRuleLoader restrictionRuleLoader;
    @Mock private AlternativeProductQueryService queryService;
    @Mock private CatalogProductMapper catalogProductMapper;
    @Mock private DietaryRuleEngine ruleEngine;
    @Mock private AlternativeProductRanker ranker;
    @Mock private RecommendationLogService logService;
    @Mock private ScanRepository scanRepository;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                restrictionRuleLoader,
                queryService,
                catalogProductMapper,
                ruleEngine,
                ranker,
                logService,
                scanRepository
        );
    }

    @Test
    void returnsEmptyWhenRequestInvalid() {
        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(null, "123", 1L));

        assertTrue(response.alternatives().isEmpty());
        verify(queryService, never()).findByBarcode(any());
    }

    @Test
    void returnsEmptyWhenSourceMissingFromCatalog() {
        when(queryService.findByBarcode("123")).thenReturn(Optional.empty());

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(1L, "123", 2L));

        assertEquals("123", response.sourceBarcode());
        assertTrue(response.alternatives().isEmpty());
    }

    @Test
    void excludesUnsafeCandidatesAndReturnsRankedSafeAlternatives() {
        CatalogProduct source = product("100", "Breakfast cereals", "Wheat flour");
        CatalogProduct safe = product("200", "Breakfast cereals", "Rice flour");
        CatalogProduct unsafe = product("300", "Breakfast cereals", "Barley malt");
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );

        when(queryService.findByBarcode("100")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(1L)).thenReturn(rules);
        when(queryService.findCandidates(source)).thenReturn(List.of(safe, unsafe));
        when(scanRepository.findByProfileIdOrderByScannedAtDesc(1L)).thenReturn(List.of(
                scan(9L, 1L, "200", "SAFE")
        ));
        when(catalogProductMapper.toProductData(safe)).thenReturn(productData("200"));
        when(catalogProductMapper.toProductData(unsafe)).thenReturn(productData("300"));
        when(ruleEngine.assess(eq(rules), any(ProductData.class)))
                .thenReturn(SafetyVerdict.safe("ok", List.of()))
                .thenReturn(SafetyVerdict.unsafe("gluten", List.of()));
        when(ranker.rank(anyList(), any())).thenReturn(List.of(
                new AlternativeProductRanker.RankedAlternative(
                        safe, new BigDecimal("0.99"), "prior_safe_scan")
        ));

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(1L, "100", 5L));

        assertEquals(1, response.alternatives().size());
        assertEquals("200", response.alternatives().getFirst().barcode());
        assertEquals("Ancient grain flakes", response.alternatives().getFirst().productName());
        assertEquals("prior_safe_scan", response.alternatives().getFirst().matchReason());

        ArgumentCaptor<List<RecommendationLogEntry>> logCaptor = ArgumentCaptor.forClass(List.class);
        verify(logService).recordAlternatives(logCaptor.capture());
        assertEquals(1, logCaptor.getValue().size());
        assertEquals("100", logCaptor.getValue().getFirst().sourceBarcode());
        assertEquals(RecommendationDiscoveryTier.TIER_A_CATALOG, logCaptor.getValue().getFirst().discoveryTier());
    }

    @Test
    void returnsEmptyWhenNoSafeCandidatesInCategory() {
        CatalogProduct source = product("100", "Breakfast cereals", "Wheat flour");
        CatalogProduct unsafe = product("300", "Breakfast cereals", "Barley malt");

        when(queryService.findByBarcode("100")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(1L)).thenReturn(List.of());
        when(queryService.findCandidates(source)).thenReturn(List.of(unsafe));
        when(catalogProductMapper.toProductData(unsafe)).thenReturn(productData("300"));
        when(ruleEngine.assess(anyList(), any(ProductData.class)))
                .thenReturn(SafetyVerdict.unsafe("gluten", List.of()));

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(1L, "100", 5L));

        assertEquals("100", response.sourceBarcode());
        assertTrue(response.alternatives().isEmpty());
        verify(ranker, never()).rank(anyList(), any());
        verify(logService, never()).recordAlternatives(anyList());
    }

    private static CatalogProduct product(String barcode, String category, String ingredients) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName("Ancient grain flakes");
        product.setBrand("Freedom Foods");
        product.setMainCategoryEn(category);
        product.setIngredientsText(ingredients);
        return product;
    }

    private static ProductData productData(String barcode) {
        return new ProductData(
                barcode,
                List.of(),
                "Rice flour",
                List.of(),
                List.of(),
                null,
                true
        );
    }

    private static Scan scan(Long id, Long profileId, String barcode, String verdict) {
        Scan scan = new Scan();
        scan.setId(id);
        scan.setProfileId(profileId);
        scan.setBarcode(barcode);
        scan.setVerdict(verdict);
        scan.setUserId(4L);
        return scan;
    }
}
