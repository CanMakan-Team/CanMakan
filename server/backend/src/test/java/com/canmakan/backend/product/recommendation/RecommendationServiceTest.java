package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
import org.springframework.test.util.ReflectionTestUtils;

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

    private ProductFeatureEncoder featureEncoder;
    private MlContentBasedRanker mlContentBasedRanker;
    private MlSparseCatalogRecommender mlSparseCatalogRecommender;
    private RecommendationService recommendationService;
    private SubstituteDiscoveryProfile freshMilksProfile;

    @BeforeEach
    void setUp() {
        ProductFeatureVectorStore vectorStore = new ProductFeatureVectorStore(new com.fasterxml.jackson.databind.ObjectMapper(), "");
        featureEncoder = new ProductFeatureEncoder(vectorStore);
        mlContentBasedRanker = new MlContentBasedRanker(featureEncoder);
        mlSparseCatalogRecommender = new MlSparseCatalogRecommender(
                queryService, featureEncoder, new SubstituteDiscoveryProfiles());
        PythonTfidfRankClient pythonTfidfRankClient = new PythonTfidfRankClient(
                new SubstituteDiscoveryProfiles(), "", 500, 2000);
        recommendationService = new RecommendationService(
                restrictionRuleLoader,
                queryService,
                discoveryProfiles,
                catalogProductMapper,
                ruleEngine,
                ranker,
                candidateFilter,
                logService,
                scanRepository,
                mlSparseCatalogRecommender,
                mlContentBasedRanker,
                pythonTfidfRankClient
        );
        ReflectionTestUtils.setField(recommendationService, "mlRecommendationEnabled", true);
        org.mockito.Mockito.lenient()
                .when(discoveryProfiles.isFlourSubstituteDiscovery(any()))
                .thenReturn(false);
        org.mockito.Mockito.lenient()
                .when(discoveryProfiles.isPeanutSpreadSubstituteDiscovery(any()))
                .thenReturn(false);
        org.mockito.Mockito.lenient()
                .when(discoveryProfiles.isIceCreamSubstituteDiscovery(any()))
                .thenReturn(false);
        org.mockito.Mockito.lenient()
                .when(discoveryProfiles.isBreadSubstituteDiscovery(any()))
                .thenReturn(false);
        org.mockito.Mockito.lenient()
                .when(discoveryProfiles.isBreakfastCerealSubstituteDiscovery(any()))
                .thenReturn(false);
        org.mockito.Mockito.lenient()
                .when(discoveryProfiles.isLowSodiumSauceSubstituteDiscovery(any()))
                .thenReturn(false);
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
        when(ruleEngine.assessForRecommendation(eq(rules), any(ProductData.class)))
                .thenReturn(SafetyVerdict.safe("ok", List.of()))
                .thenReturn(SafetyVerdict.unsafe("gluten", List.of()));
        when(candidateFilter.isAcceptableAlternative(eq(rules), any(SafetyVerdict.class), any(CatalogProduct.class)))
                .thenReturn(true)
                .thenReturn(false);

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(1L, "100", 5L));

        assertEquals(1, response.alternatives().size());
        assertEquals("200", response.alternatives().getFirst().barcode());
        assertEquals("Ancient grain flakes", response.alternatives().getFirst().productName());
        assertEquals("ml_prior_safe_scan", response.alternatives().getFirst().matchReason());

        ArgumentCaptor<List<RecommendationLogEntry>> logCaptor = ArgumentCaptor.forClass(List.class);
        verify(logService).recordAlternatives(logCaptor.capture());
        assertEquals(1, logCaptor.getValue().size());
        assertEquals("100", logCaptor.getValue().getFirst().sourceBarcode());
        assertEquals(RecommendationDiscoveryTier.TIER_A_CATALOG, logCaptor.getValue().getFirst().discoveryTier());
        verify(ranker, never()).rankSameCategory(anyList(), any());
    }

    @Test
    void returnsEmptyWhenNoSafeCandidatesInCategoryAndNoSubstituteProfile() {
        CatalogProduct source = product("100", "Snacks", "Wheat flour", null);
        CatalogProduct unsafe = product("300", "Snacks", "Barley malt", null);

        when(queryService.findByBarcode("100")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(1L)).thenReturn(List.of());
        when(queryService.findSameCategoryCandidates(source)).thenReturn(List.of(unsafe));
        when(discoveryProfiles.forSourceProduct(any())).thenReturn(Optional.empty());
        when(catalogProductMapper.toProductData(unsafe)).thenReturn(productData("300"));
        when(ruleEngine.assessForRecommendation(anyList(), any(ProductData.class)))
                .thenReturn(SafetyVerdict.unsafe("gluten", List.of()));
        when(candidateFilter.isAcceptableAlternative(anyList(), any(SafetyVerdict.class), any(CatalogProduct.class)))
                .thenReturn(false);

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(1L, "100", 5L));

        assertEquals("100", response.sourceBarcode());
        assertTrue(response.alternatives().isEmpty());
        verify(ranker, never()).rankSameCategory(anyList(), any());
        verify(ranker, never()).rankSubstituteTags(any(), anyList(), any(), any());
        verify(logService, never()).recordAlternatives(anyList());
    }

    @Test
    void fallsBackToTagSubstitutesWhenSameCategoryHasNoAcceptableCandidates() {
        CatalogProduct source = sparseFarmhouseFreshMilk();
        CatalogProduct oatDrink = namedProduct(
                "7394376618253",
                "Oatly barista edition",
                "Oat-based drinks",
                "water, oats",
                "en:milk-substitutes,en:oat-based-drinks");
        CatalogProduct unsweetenedSoy = namedProduct(
                "8850025000521",
                "Soya Milk Unsweetened",
                "Unsweetened plain soy-based drinks",
                "Soy milk 99.6%, Calcium Carbonate",
                "en:milk-substitutes,en:unsweetened-plain-soy-based-drinks");
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE),
                new RestrictionRule("LOW_SUGAR", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE)
        );
        SafetyVerdict oatWarning = SafetyVerdict.warning(
                "unresolved",
                List.of(new Finding("UNRESOLVED", "dipotassium phosphate", "could not be analysed")));
        SafetyVerdict soySafe = SafetyVerdict.safe("ok", List.of());

        when(queryService.findByBarcode("8888200602857")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(3L)).thenReturn(rules);
        when(discoveryProfiles.forSourceProduct(source)).thenReturn(Optional.of(freshMilksProfile));
        when(queryService.findSubstituteTagCandidates(source, freshMilksProfile))
                .thenReturn(List.of(oatDrink, unsweetenedSoy));
        when(scanRepository.findByProfileIdOrderByScannedAtDesc(3L)).thenReturn(List.of());
        when(catalogProductMapper.toProductData(oatDrink)).thenReturn(productData("7394376618253"));
        when(catalogProductMapper.toProductData(unsweetenedSoy)).thenReturn(productData("8850025000521"));
        when(ruleEngine.assessForRecommendation(eq(rules), any(ProductData.class)))
                .thenReturn(oatWarning)
                .thenReturn(soySafe);
        when(candidateFilter.isAcceptableAlternative(eq(rules), eq(oatWarning), eq(oatDrink))).thenReturn(true);
        when(candidateFilter.isAcceptableAlternative(eq(rules), eq(soySafe), eq(unsweetenedSoy))).thenReturn(true);

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(3L, "8888200602857", 5L));

        assertEquals(2, response.alternatives().size());
        assertEquals("8850025000521", response.alternatives().getFirst().barcode());
        assertEquals("ml_unsweetened_substitute", response.alternatives().getFirst().matchReason());
        verify(queryService).findSubstituteTagCandidates(source, freshMilksProfile);
        verify(ranker, never()).rankSubstituteTags(any(), anyList(), any(), any());

        ArgumentCaptor<List<RecommendationLogEntry>> logCaptor = ArgumentCaptor.forClass(List.class);
        verify(logService).recordAlternatives(logCaptor.capture());
        assertEquals(RecommendationDiscoveryTier.TIER_A_CATALOG, logCaptor.getValue().getFirst().discoveryTier());
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
        when(discoveryProfiles.forSourceProduct(source)).thenReturn(Optional.of(wheatFloursProfile));
        when(queryService.findSubstituteTagCandidates(source, wheatFloursProfile)).thenReturn(List.of(brownRiceFlour));
        when(scanRepository.findByProfileIdOrderByScannedAtDesc(1L)).thenReturn(List.of());
        when(catalogProductMapper.toProductData(otherWheatFlour)).thenReturn(productData("8886350000011"));
        when(catalogProductMapper.toProductData(brownRiceFlour)).thenReturn(productData("8887501030642"));
        when(ruleEngine.assessForRecommendation(eq(rules), any(ProductData.class)))
                .thenReturn(SafetyVerdict.unsafe("gluten", List.of(new Finding("GLUTEN", "wheat", "gluten"))))
                .thenReturn(SafetyVerdict.safe("ok", List.of()));
        when(candidateFilter.isAcceptableAlternative(eq(rules), any(SafetyVerdict.class), any(CatalogProduct.class)))
                .thenReturn(false)
                .thenReturn(true);

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(1L, "4894514060287", 5L));

        assertEquals(1, response.alternatives().size());
        assertEquals("8887501030642", response.alternatives().getFirst().barcode());
        verify(queryService).findSubstituteTagCandidates(source, wheatFloursProfile);
        verify(ranker, never()).rankSubstituteTags(any(), anyList(), any(), any());
    }

    @Test
    void fallsBackToGlutenFreeBreakfastCerealsWhenCategoryHasNoAcceptableCandidates() {
        CatalogProduct source = product(
                "0038527591039",
                "Breakfast cereals",
                "Whole Grain Oat Flour, Whole Wheat Flour",
                "en:breakfast-cereals");
        CatalogProduct otherCereal = product(
                "4800361355872",
                "Breakfast cereals",
                "Whole Grain Wheat, Wheat Flour, Barley Malt Extract",
                "en:breakfast-cereals");
        CatalogProduct glutenFreeCereal = product(
                "9315090200706",
                "Breakfast cereals",
                "rice flour, yellow corn flour, sorghum flour",
                "Gluten free Breakfast cereals");
        SubstituteDiscoveryProfile breakfastCerealsProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Breakfast cereals").orElseThrow();
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );

        when(queryService.findByBarcode("0038527591039")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(1L)).thenReturn(rules);
        when(queryService.findSameCategoryCandidates(source)).thenReturn(List.of(otherCereal));
        when(discoveryProfiles.forSourceProduct(source))
                .thenReturn(Optional.of(breakfastCerealsProfile));
        when(queryService.findSubstituteTagCandidates(source, breakfastCerealsProfile))
                .thenReturn(List.of(glutenFreeCereal));
        when(scanRepository.findByProfileIdOrderByScannedAtDesc(1L)).thenReturn(List.of());
        when(catalogProductMapper.toProductData(otherCereal)).thenReturn(productData("4800361355872"));
        when(catalogProductMapper.toProductData(glutenFreeCereal)).thenReturn(productData("9315090200706"));
        when(ruleEngine.assessForRecommendation(eq(rules), any(ProductData.class)))
                .thenReturn(SafetyVerdict.unsafe("gluten", List.of(new Finding("GLUTEN", "wheat", "gluten"))))
                .thenReturn(SafetyVerdict.safe("ok", List.of()));
        when(candidateFilter.isAcceptableAlternative(eq(rules), any(SafetyVerdict.class), any(CatalogProduct.class)))
                .thenReturn(false)
                .thenReturn(true);

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(1L, "0038527591039", 5L));

        assertEquals(1, response.alternatives().size());
        assertEquals("9315090200706", response.alternatives().getFirst().barcode());
        verify(queryService).findSubstituteTagCandidates(source, breakfastCerealsProfile);
        verify(ranker, never()).rankSubstituteTags(any(), anyList(), any(), any());
    }

    @Test
    void fallsBackToPeanutFreeSpreadsWhenPeanutButterCategoryHasNoAcceptableCandidates() {
        CatalogProduct source = namedProduct(
                "0045300005409",
                "Crunchy Peanut Butter",
                "Crunchy peanut butters",
                "Roasted Peanuts, Sugar, Salt",
                "en:spreads,en:peanut-butters,en:crunchy-peanut-butters");
        source.setAllergens("en:peanuts");
        CatalogProduct otherPeanutButter = namedProduct(
                "0051500710166",
                "peanut butter",
                "Peanut butters",
                "Peanuts, salt",
                "en:spreads,en:peanut-butters");
        otherPeanutButter.setAllergens("en:peanuts");
        CatalogProduct tahini = namedProduct(
                "8888536703136",
                "Organic Tahini (Unhulled)",
                "White tahini",
                "Organic sesame seeds",
                "en:oilseed-purees,en:cereal-butters,en:tahini");
        SubstituteDiscoveryProfile peanutButterProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Crunchy peanut butters").orElseThrow();
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("PEANUT", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );
        SafetyVerdict peanutUnsafe = SafetyVerdict.unsafe(
                "peanut",
                List.of(new Finding("PEANUT", "peanuts", "peanuts")));
        SafetyVerdict safe = SafetyVerdict.safe("ok", List.of());

        when(queryService.findByBarcode("0045300005409")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(3L)).thenReturn(rules);
        when(queryService.findSameCategoryCandidates(source)).thenReturn(List.of(otherPeanutButter));
        when(discoveryProfiles.forSourceProduct(source))
                .thenReturn(Optional.of(peanutButterProfile));
        when(queryService.findSubstituteTagCandidates(source, peanutButterProfile))
                .thenReturn(List.of(tahini));
        when(scanRepository.findByProfileIdOrderByScannedAtDesc(3L)).thenReturn(List.of());
        when(catalogProductMapper.toProductData(otherPeanutButter)).thenReturn(productData("0051500710166"));
        when(catalogProductMapper.toProductData(tahini)).thenReturn(productData("8888536703136"));
        when(ruleEngine.assessForRecommendation(eq(rules), any(ProductData.class)))
                .thenReturn(peanutUnsafe)
                .thenReturn(safe);
        when(candidateFilter.isAcceptableAlternative(eq(rules), eq(peanutUnsafe), eq(otherPeanutButter)))
                .thenReturn(false);
        when(candidateFilter.isAcceptableAlternative(eq(rules), eq(safe), eq(tahini))).thenReturn(true);

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(3L, "0045300005409", 5L));

        assertEquals(1, response.alternatives().size());
        assertEquals("8888536703136", response.alternatives().getFirst().barcode());
        verify(queryService).findSubstituteTagCandidates(source, peanutButterProfile);
        verify(ranker, never()).rankSubstituteTags(any(), anyList(), any(), any());
    }

    @Test
    @DisplayName("ML disabled: sparse source uses heuristic ranker, not ML re-rank")
    void usesHeuristicRankerWhenMlFeatureFlagDisabled() {
        ReflectionTestUtils.setField(recommendationService, "mlRecommendationEnabled", false);

        CatalogProduct source = sparseFarmhouseFreshMilk();
        CatalogProduct unsweetenedSoy = namedProduct(
                "8850025000521",
                "Soya Milk Unsweetened",
                "Unsweetened plain soy-based drinks",
                "Soy milk 99.6%, Calcium Carbonate",
                "en:milk-substitutes,en:unsweetened-plain-soy-based-drinks");
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE),
                new RestrictionRule("LOW_SUGAR", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE)
        );
        SafetyVerdict soySafe = SafetyVerdict.safe("ok", List.of());

        when(queryService.findByBarcode("8888200602857")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(3L)).thenReturn(rules);
        when(discoveryProfiles.forSourceProduct(source)).thenReturn(Optional.of(freshMilksProfile));
        when(queryService.findSubstituteTagCandidates(source, freshMilksProfile))
                .thenReturn(List.of(unsweetenedSoy));
        when(scanRepository.findByProfileIdOrderByScannedAtDesc(3L)).thenReturn(List.of());
        when(catalogProductMapper.toProductData(unsweetenedSoy)).thenReturn(productData("8850025000521"));
        when(ruleEngine.assessForRecommendation(eq(rules), any(ProductData.class)))
                .thenReturn(soySafe);
        when(candidateFilter.isAcceptableAlternative(eq(rules), eq(soySafe), eq(unsweetenedSoy))).thenReturn(true);
        when(ranker.rankSubstituteTags(eq(source), anyList(), any(), eq(freshMilksProfile))).thenReturn(List.of(
                new AlternativeProductRanker.RankedAlternative(
                        unsweetenedSoy, new BigDecimal("0.90"), "substitute_category")
        ));

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(3L, "8888200602857", 5L));

        assertEquals(1, response.alternatives().size());
        assertEquals("substitute_category", response.alternatives().getFirst().matchReason());
        verify(ranker).rankSubstituteTags(eq(source), anyList(), any(), eq(freshMilksProfile));
        verify(queryService).findSubstituteTagCandidates(source, freshMilksProfile);
        verify(queryService, never()).findExpandedSubstituteCandidates(eq(source), any());

        ArgumentCaptor<List<RecommendationLogEntry>> logCaptor = ArgumentCaptor.forClass(List.class);
        verify(logService).recordAlternatives(logCaptor.capture());
        assertEquals(RecommendationDiscoveryTier.TIER_A_CATALOG, logCaptor.getValue().getFirst().discoveryTier());
    }

    @Test
    @DisplayName("Expands pool when Tier A finds fewer than 5 SAFE alternatives")
    void expandsWithMlDiscoveryWhenTierAHasFewerThanFiveSafeCandidates() {
        CatalogProduct source = namedProduct(
                "0078895129779",
                "Soy Sauce",
                "Soy sauces",
                "Water, salt, soybeans, wheat flour",
                "en:soy-sauces");
        CatalogProduct wheatSoy = namedProduct(
                "4965249200528",
                "Soy Sauce",
                "Soy sauces",
                "Water, salt, soybeans, wheat flour",
                "en:soy-sauces");
        CatalogProduct kikkomanGf = namedProduct(
                "4901515129889",
                "Gluten Free Soy Sauce",
                "Soy sauces",
                "Water, Soybeans, Rice, Salt",
                "Gluten Free sauces");
        CatalogProduct labeledGf = namedProduct(
                "9343317000624",
                "Gluten Free Sauce",
                "Sauces",
                "Water, soybeans, salt",
                "en:sauces");
        labeledGf.setLabelsTags("en:no-gluten");
        SubstituteDiscoveryProfile soySauceProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Soy sauces").orElseThrow();
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );
        SafetyVerdict glutenUnsafe = SafetyVerdict.unsafe(
                "gluten",
                List.of(new Finding("GLUTEN", "wheat", "gluten")));
        SafetyVerdict safe = SafetyVerdict.safe("ok", List.of());

        when(queryService.findByBarcode("0078895129779")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(1L)).thenReturn(rules);
        when(queryService.findSameCategoryCandidates(source)).thenReturn(List.of(wheatSoy));
        when(discoveryProfiles.forSourceProduct(source)).thenReturn(Optional.of(soySauceProfile));
        when(queryService.findSubstituteTagCandidates(source, soySauceProfile)).thenReturn(List.of(kikkomanGf));
        when(queryService.findExpandedSubstituteCandidates(eq(source), any()))
                .thenReturn(List.of(kikkomanGf, labeledGf));
        when(scanRepository.findByProfileIdOrderByScannedAtDesc(1L)).thenReturn(List.of());
        when(catalogProductMapper.toProductData(wheatSoy)).thenReturn(productData("4965249200528"));
        when(catalogProductMapper.toProductData(kikkomanGf)).thenReturn(productData("4901515129889"));
        when(catalogProductMapper.toProductData(labeledGf)).thenReturn(productData("9343317000624"));
        when(ruleEngine.assessForRecommendation(eq(rules), any(ProductData.class)))
                .thenReturn(glutenUnsafe)
                .thenReturn(safe)
                .thenReturn(safe);
        when(candidateFilter.isAcceptableAlternative(eq(rules), eq(glutenUnsafe), eq(wheatSoy))).thenReturn(false);
        when(candidateFilter.isAcceptableAlternative(eq(rules), eq(safe), eq(kikkomanGf))).thenReturn(true);
        when(candidateFilter.isAcceptableAlternative(eq(rules), eq(safe), eq(labeledGf))).thenReturn(true);

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(1L, "0078895129779", 5L));

        assertEquals(2, response.alternatives().size());
        verify(queryService, atLeastOnce()).findExpandedSubstituteCandidates(eq(source), any());
        ArgumentCaptor<List<RecommendationLogEntry>> logCaptor = ArgumentCaptor.forClass(List.class);
        verify(logService).recordAlternatives(logCaptor.capture());
        assertEquals(RecommendationDiscoveryTier.TIER_C_ML_SPARSE, logCaptor.getValue().getFirst().discoveryTier());
    }

    @Test
    @DisplayName("MVP: LLM discovery is not used even when the catalog pool is empty")
    void doesNotUseLlmDiscoveryWhenTierAAndTierCEmpty() {
        CatalogProduct source = sparseFarmhouseFreshMilk();
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE)
        );

        when(queryService.findByBarcode("8888200602857")).thenReturn(Optional.of(source));
        when(restrictionRuleLoader.load(3L)).thenReturn(rules);
        when(discoveryProfiles.forSourceProduct(source)).thenReturn(Optional.of(freshMilksProfile));
        when(queryService.findSubstituteTagCandidates(source, freshMilksProfile)).thenReturn(List.of());

        AlternativeProductResponse response = recommendationService.recommend(
                new RecommendationRequest(3L, "8888200602857", 5L));

        assertTrue(response.alternatives().isEmpty());
        verify(logService, never()).recordAlternatives(anyList());
    }

    private static CatalogProduct sparseFarmhouseFreshMilk() {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode("8888200602857");
        product.setProductName("Farmhouse Fresh Milk");
        product.setBrand("Farmhouse");
        product.setMainCategoryEn("Fresh milks");
        product.setCategoryTags("en:dairies,en:milks,en:fresh-milks");
        product.setIngredientsText("Fresh milks");
        product.setAllergens("en:milk");
        return product;
    }

    private static CatalogProduct product(
            String barcode,
            String category,
            String ingredients,
            String categoryTags) {
        return namedProduct(barcode, "Ancient grain flakes", category, ingredients, categoryTags);
    }

    private static CatalogProduct namedProduct(
            String barcode,
            String productName,
            String category,
            String ingredients,
            String categoryTags) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName(productName);
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
