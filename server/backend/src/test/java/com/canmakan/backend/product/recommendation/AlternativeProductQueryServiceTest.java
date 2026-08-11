package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC5: AlternativeProductQueryService")
class AlternativeProductQueryServiceTest {

    @Mock
    private CatalogProductRepository catalogProductRepository;

    private AlternativeProductQueryService queryService;
    private SubstituteDiscoveryProfile freshMilksProfile;

    @BeforeEach
    void setUp() {
        queryService = new AlternativeProductQueryService(catalogProductRepository);
        freshMilksProfile = new SubstituteDiscoveryProfiles().forSourceCategory("Fresh milks").orElseThrow();
    }

    @Test
    void findCandidatesUsesSameCategoryAndExcludesSource() {
        CatalogProduct source = product("100", "Breakfast cereals", "Oats, Wheat", null);
        CatalogProduct candidate = product("200", "Breakfast cereals", "Rice flour", null);

        when(catalogProductRepository.findCandidatesByCategory("Breakfast cereals", "100"))
                .thenReturn(List.of(candidate));

        List<CatalogProduct> results = queryService.findCandidates(source);

        assertEquals(1, results.size());
        assertEquals("200", results.getFirst().getBarcode());
        verify(catalogProductRepository).findCandidatesByCategory("Breakfast cereals", "100");
    }

    @Test
    void findCandidatesReturnsEmptyWhenSourceNotEligible() {
        CatalogProduct source = product("100", null, "Oats", null);

        assertTrue(queryService.findCandidates(source).isEmpty());
        assertTrue(queryService.findCandidates(null).isEmpty());
    }

    @Test
    void findCandidatesLimitsToFiftyRows() {
        CatalogProduct source = product("100", "Groceries", "Salt", null);
        List<CatalogProduct> many = IntStream.range(0, 60)
                .mapToObj(i -> product(String.valueOf(200 + i), "Groceries", "Salt", null))
                .toList();

        when(catalogProductRepository.findCandidatesByCategory("Groceries", "100"))
                .thenReturn(many);

        assertEquals(50, queryService.findCandidates(source).size());
    }

    @Test
    void findSubstituteTagCandidatesQueriesIncludeTagsAndDedupes() {
        CatalogProduct source = product("8888200602857", "Fresh milks", "Fresh milk", null);
        CatalogProduct oatly = product(
                "7394376618253",
                "Oat-based drinks",
                "water, oats",
                "en:dairy-substitutes,en:milk-substitutes,en:oat-based-drinks");
        CatalogProduct duplicate = product(
                "7394376618253",
                "Oat-based drinks",
                "water, oats",
                "en:dairy-substitutes,en:milk-substitutes,en:oat-based-drinks");

        when(catalogProductRepository.findCandidatesByCategoryTag("en:milk-substitutes", "8888200602857"))
                .thenReturn(List.of(oatly));
        when(catalogProductRepository.findCandidatesByCategoryTag("en:dairy-substitutes", "8888200602857"))
                .thenReturn(List.of(duplicate));

        List<CatalogProduct> results = queryService.findSubstituteTagCandidates(source, freshMilksProfile);

        assertEquals(1, results.size());
        assertEquals("7394376618253", results.getFirst().getBarcode());
    }

    @Test
    void findSubstituteTagCandidatesExcludesRowsWithoutIncludeTags() {
        CatalogProduct source = product("8888200602857", "Fresh milks", "Fresh milk", null);
        CatalogProduct unrelated = product(
                "999",
                "Groceries",
                "Salt",
                "en:beverages");

        when(catalogProductRepository.findCandidatesByCategoryTag("en:milk-substitutes", "8888200602857"))
                .thenReturn(List.of(unrelated));
        when(catalogProductRepository.findCandidatesByCategoryTag("en:dairy-substitutes", "8888200602857"))
                .thenReturn(List.of());

        assertTrue(queryService.findSubstituteTagCandidates(source, freshMilksProfile).isEmpty());
    }

    @Test
    void findSubstituteTagCandidatesQueriesGlutenFreeFlourForWheatFlourSource() {
        CatalogProduct source = product(
                "4894514060287",
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

        when(catalogProductRepository.findCandidatesByCategoryTag("en:gluten-free-flour", "4894514060287"))
                .thenReturn(List.of(brownRiceFlour));

        List<CatalogProduct> results = queryService.findSubstituteTagCandidates(source, wheatFloursProfile);

        assertEquals(1, results.size());
        assertEquals("8887501030642", results.getFirst().getBarcode());
    }

    @Test
    void findByBarcodeDelegatesToRepository() {
        CatalogProduct product = product("100", "Groceries", "Salt", null);
        when(catalogProductRepository.findById("100")).thenReturn(Optional.of(product));

        assertTrue(queryService.findByBarcode("100").isPresent());
        assertEquals("100", queryService.findByBarcode("100").get().getBarcode());
    }

    private static CatalogProduct product(
            String barcode,
            String category,
            String ingredients,
            String categoryTags) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName("Product " + barcode);
        product.setMainCategoryEn(category);
        product.setIngredientsText(ingredients);
        product.setCategoryTags(categoryTags);
        return product;
    }
}
