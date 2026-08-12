package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.dietaryprofile.service.RestrictionRuleLoader;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanRepository;
import com.canmakan.backend.product.verdict.DietaryRuleEngine;
import com.canmakan.backend.product.verdict.Finding;
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
    @Mock private SubstituteDiscoveryProfiles discoveryProfiles;
    @Mock private CatalogProductMapper catalogProductMapper;
    @Mock private DietaryRuleEngine ruleEngine;
    @Mock private AlternativeProductRanker ranker;
    @Mock private AlternativeCandidateFilter candidateFilter;
    @Mock private RecommendationLogService logService;
    @Mock private ScanRepository scanRepository;

    private RecommendationService recommendationService;
    private SubstituteDiscoveryProfile freshMilksProfile;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                restrictionRuleLoader,
                queryService,
                discoveryProfiles,
                catalogProductMapper,
                ruleEngine,
                ranker,
                candidateFilter,
                logService,
                scanRepository
        );
        freshMilksProfile = new SubstituteDiscoveryProfiles().forSourceCategory("Fresh milks").orElseThrow();
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
        CatalogProduct source = product("100", "Breakfast cereals", "Wheat flour", null);
        CatalogProduct safe = product("200", "Breakfast cereals", "Rice flour", null);
        CatalogProduct unsafe = product("300", "Breakfast cereals", "Barley malt", null);
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );

        when(queryService.findByBarcode("100")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(1L)).thenReturn(rules);
        when(queryService.findSameCategoryCandidates(source)).thenReturn(List.of(safe, unsafe));
        when(scanRepository.findByProfileIdOrderByScannedAtDesc(1L)).thenReturn(List.of(
                scan(9L, 1L, "200", "SAFE")
        ));
        when(catalogProductMapper.toProductData(safe)).thenReturn(productData("200"));
        when(catalogProductMapper.toProductData(unsafe)).thenReturn(productData("300"));
        when(ruleEngine.assess(eq(rules), any(ProductData.class)))
                .thenReturn(SafetyVerdict.safe("ok", List.of()))
                .thenReturn(SafetyVerdict.unsafe("gluten", List.of()));
        when(candidateFilter.isAcceptableAlternative(eq(rules), any(SafetyVerdict.class), any(CatalogProduct.class)))
                .thenReturn(true)
                .thenReturn(false);
        when(ranker.rankSameCategory(anyList(), any())).thenReturn(List.of(
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
    void returnsEmptyWhenNoSafeCandidatesInCategoryAndNoSubstituteProfile() {
        CatalogProduct source = product("100", "Breakfast cereals", "Wheat flour", null);
        CatalogProduct unsafe = product("300", "Breakfast cereals", "Barley malt", null);

        when(queryService.findByBarcode("100")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(1L)).thenReturn(List.of());
        when(queryService.findSameCategoryCandidates(source)).thenReturn(List.of(unsafe));
        when(discoveryProfiles.forSourceCategory("Breakfast cereals")).thenReturn(Optional.empty());
        when(catalogProductMapper.toProductData(unsafe)).thenReturn(productData("300"));
        when(ruleEngine.assess(anyList(), any(ProductData.class)))
                .thenReturn(SafetyVerdict.unsafe("gluten", List.of()));
        when(candidateFilter.isAcceptableAlternative(anyList(), any(SafetyVerdict.class), any(CatalogProduct.class)))
                .thenReturn(false);

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(1L, "100", 5L));

        assertEquals("100", response.sourceBarcode());
        assertTrue(response.alternatives().isEmpty());
        verify(ranker, never()).rankSameCategory(anyList(), any());
        verify(ranker, never()).rankSubstituteTags(anyList(), any(), any());
        verify(logService, never()).recordAlternatives(anyList());
    }

    @Test
    void fallsBackToTagSubstitutesWhenSameCategoryHasNoAcceptableCandidates() {
        CatalogProduct source = product("8888200602857", "Fresh milks", "Fresh milk", null);
        CatalogProduct dairyMilk = product("8888200132217", "Fresh milks", "100% Fresh Milk", null);
        CatalogProduct oatDrink = product(
                "7394376618253",
                "Oat-based drinks",
                "water, oats",
                "en:milk-substitutes,en:oat-based-drinks");
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE)
        );
        SafetyVerdict dairyWarning = SafetyVerdict.warning(
                "dairy",
                List.of(new Finding("DAIRY", "milk", "milk matches DAIRY restriction.")));
        SafetyVerdict oatWarning = SafetyVerdict.warning(
                "unresolved",
                List.of(new Finding("UNRESOLVED", "dipotassium phosphate", "could not be analysed")));

        when(queryService.findByBarcode("8888200602857")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(3L)).thenReturn(rules);
        when(queryService.findSameCategoryCandidates(source)).thenReturn(List.of(dairyMilk));
        when(discoveryProfiles.forSourceCategory("Fresh milks")).thenReturn(Optional.of(freshMilksProfile));
        when(queryService.findSubstituteTagCandidates(source, freshMilksProfile)).thenReturn(List.of(oatDrink));
        when(scanRepository.findByProfileIdOrderByScannedAtDesc(3L)).thenReturn(List.of());
        when(catalogProductMapper.toProductData(dairyMilk)).thenReturn(productData("8888200132217"));
        when(catalogProductMapper.toProductData(oatDrink)).thenReturn(productData("7394376618253"));
        when(ruleEngine.assess(eq(rules), any(ProductData.class)))
                .thenReturn(dairyWarning)
                .thenReturn(oatWarning);
        when(candidateFilter.isAcceptableAlternative(eq(rules), eq(dairyWarning), eq(dairyMilk))).thenReturn(false);
        when(candidateFilter.isAcceptableAlternative(eq(rules), eq(oatWarning), eq(oatDrink))).thenReturn(true);
        when(ranker.rankSubstituteTags(anyList(), any(), eq(freshMilksProfile))).thenReturn(List.of(
                new AlternativeProductRanker.RankedAlternative(
                        oatDrink, new BigDecimal("0.98"), "substitute_category")
        ));

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(3L, "8888200602857", 5L));

        assertEquals(1, response.alternatives().size());
        assertEquals("7394376618253", response.alternatives().getFirst().barcode());
        assertEquals("substitute_category", response.alternatives().getFirst().matchReason());
        verify(queryService).findSubstituteTagCandidates(source, freshMilksProfile);
        verify(ranker).rankSubstituteTags(anyList(), any(), eq(freshMilksProfile));
    }

    @Test
    void fallsBackToGlutenFreeFlourWhenWheatFlourCategoryHasNoAcceptableCandidates() {
        CatalogProduct source = product(
                "4894514060287",
                "Wheat flours",
                "Wheat Flour",
                "en:wheat-flours");
        CatalogProduct otherWheatFlour = product(
                "8886350000011",
                "Wheat flours",
                "Wheat Flour",
                "en:wheat-flours");
        CatalogProduct brownRiceFlour = product(
                "8887501030642",
                "Brown Rice Flour",
                "Organic Brown Rice",
                "en:no-gluten,en:gluten-free,en:gluten-free-flour");
        SubstituteDiscoveryProfile wheatFloursProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Wheat flours").orElseThrow();
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );

        when(queryService.findByBarcode("4894514060287")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(1L)).thenReturn(rules);
        when(queryService.findSameCategoryCandidates(source)).thenReturn(List.of(otherWheatFlour));
        when(discoveryProfiles.forSourceCategory("Wheat flours")).thenReturn(Optional.of(wheatFloursProfile));
        when(queryService.findSubstituteTagCandidates(source, wheatFloursProfile)).thenReturn(List.of(brownRiceFlour));
        when(scanRepository.findByProfileIdOrderByScannedAtDesc(1L)).thenReturn(List.of());
        when(catalogProductMapper.toProductData(otherWheatFlour)).thenReturn(productData("8886350000011"));
        when(catalogProductMapper.toProductData(brownRiceFlour)).thenReturn(productData("8887501030642"));
        when(ruleEngine.assess(eq(rules), any(ProductData.class)))
                .thenReturn(SafetyVerdict.unsafe("gluten", List.of(new Finding("GLUTEN", "wheat", "gluten"))))
                .thenReturn(SafetyVerdict.safe("ok", List.of()));
        when(candidateFilter.isAcceptableAlternative(eq(rules), any(SafetyVerdict.class), any(CatalogProduct.class)))
                .thenReturn(false)
                .thenReturn(true);
        when(ranker.rankSubstituteTags(anyList(), any(), eq(wheatFloursProfile))).thenReturn(List.of(
                new AlternativeProductRanker.RankedAlternative(
                        brownRiceFlour, new BigDecimal("0.95"), "substitute_category")
        ));

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(1L, "4894514060287", 5L));

        assertEquals(1, response.alternatives().size());
        assertEquals("8887501030642", response.alternatives().getFirst().barcode());
        assertEquals("substitute_category", response.alternatives().getFirst().matchReason());
        verify(queryService).findSubstituteTagCandidates(source, wheatFloursProfile);
        verify(ranker).rankSubstituteTags(anyList(), any(), eq(wheatFloursProfile));
    }

    private static CatalogProduct product(
            String barcode,
            String category,
            String ingredients,
            String categoryTags) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName("Ancient grain flakes");
        product.setBrand("Freedom Foods");
        product.setMainCategoryEn(category);
        product.setIngredientsText(ingredients);
        product.setCategoryTags(categoryTags);
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
