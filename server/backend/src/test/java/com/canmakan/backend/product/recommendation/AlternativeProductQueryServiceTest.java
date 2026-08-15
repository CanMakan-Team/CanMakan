package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    void findCandidatesAllowsSourceWithoutIngredientsWhenCategoryPresent() {
        CatalogProduct source = product("100", "Groceries", null, null);
        CatalogProduct candidate = product("200", "Groceries", null, null);

        when(catalogProductRepository.findCandidatesByCategory("Groceries", "100"))
                .thenReturn(List.of(candidate));

        List<CatalogProduct> results = queryService.findCandidates(source);

        assertEquals(1, results.size());
        assertEquals("200", results.getFirst().getBarcode());
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
    void findSubstituteTagCandidatesForMilkProfileDoesNotQueryLabelTags() {
        CatalogProduct source = product("8888200602857", "Fresh milks", "Fresh milk", null);

        for (String tag : freshMilksProfile.includeTags()) {
            when(catalogProductRepository.findCandidatesByCategoryTag(tag, "8888200602857"))
                    .thenReturn(List.of());
        }

        assertTrue(queryService.findSubstituteTagCandidates(source, freshMilksProfile).isEmpty());

        verify(catalogProductRepository, never()).findCandidatesByLabelTag(any(), any());
    }

    @Test
    void findSubstituteTagCandidatesQueriesGlutenFreeBreakfastCerealsTag() {
        CatalogProduct source = product(
                "0038527591039",
                "Breakfast cereals",
                "Whole Grain Oat Flour, Whole Wheat Flour",
                "en:breakfast-cereals");
        CatalogProduct glutenFreeGranola = product(
                "9315090200706",
                "Breakfast cereals",
                "rice flour, yellow corn flour, sorghum flour",
                "Gluten free Breakfast cereals");
        SubstituteDiscoveryProfile breakfastCerealsProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Breakfast cereals").orElseThrow();

        when(catalogProductRepository.findCandidatesByCategoryTag(
                "Gluten free Breakfast cereals",
                "0038527591039"))
                .thenReturn(List.of(glutenFreeGranola));

        List<CatalogProduct> results = queryService.findSubstituteTagCandidates(source, breakfastCerealsProfile);

        assertEquals(1, results.size());
        assertEquals("9315090200706", results.getFirst().getBarcode());
    }

    @Test
    void findSubstituteTagCandidatesQueriesIceCreamSorbetTagForIceCreamSource() {
        CatalogProduct source = product(
                "8714100638415",
                "Ice cream bars",
                "Reconstituted skimmed MILK, sugar",
                "en:ice-cream-bars");
        CatalogProduct sorbet = product(
                "0797776401178",
                "Ice cream tubs",
                "Acai pulp, coconut milk",
                "ice-creams-and-sorbets");
        SubstituteDiscoveryProfile iceCreamProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Ice cream bars").orElseThrow();

        when(catalogProductRepository.findCandidatesByCategoryTag("ice-creams-and-sorbets", "8714100638415"))
                .thenReturn(List.of(sorbet));
        when(catalogProductRepository.findCandidatesByCategoryTag("en:ice-creams-and-sorbets", "8714100638415"))
                .thenReturn(List.of());

        List<CatalogProduct> results = queryService.findSubstituteTagCandidates(source, iceCreamProfile);

        assertEquals(1, results.size());
        assertEquals("0797776401178", results.getFirst().getBarcode());
    }

    @Test
    void findSubstituteTagCandidatesQueriesGlutenFreeSaucesForSoySauceSource() {
        CatalogProduct source = product(
                "0078895129779",
                "Soy sauces",
                "Water, salt, soybeans, wheat flour",
                "en:soy-sauces");
        CatalogProduct glutenFreeSauce = product(
                "4901515129889",
                "Soy sauces",
                "Water, Soybeans, Rice, Salt",
                "Gluten Free sauces");
        SubstituteDiscoveryProfile soySauceProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Soy sauces").orElseThrow();

        when(catalogProductRepository.findCandidatesByCategoryTag("Gluten Free sauces", "0078895129779"))
                .thenReturn(List.of(glutenFreeSauce));

        List<CatalogProduct> results = queryService.findSubstituteTagCandidates(source, soySauceProfile);

        assertEquals(1, results.size());
        assertEquals("4901515129889", results.getFirst().getBarcode());
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
    void findSubstituteTagCandidatesQueriesHumanReadableGlutenFreeFlourTag() {
        CatalogProduct source = product(
                "9555064500016",
                "Wheat flours",
                "Wheat Flour, Iron, Niacin",
                "en:wheat-flours");
        CatalogProduct buckwheatFlour = product(
                "8887501030697",
                "Groceries",
                "Buckwheat Flour",
                "No gluten, Gluten-free, Gluten-free flour");
        SubstituteDiscoveryProfile wheatFloursProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Wheat flours").orElseThrow();

        when(catalogProductRepository.findCandidatesByCategoryTag("en:gluten-free-flour", "9555064500016"))
                .thenReturn(List.of());
        when(catalogProductRepository.findCandidatesByCategoryTag("Gluten free flour", "9555064500016"))
                .thenReturn(List.of());
        when(catalogProductRepository.findCandidatesByCategoryTag("Gluten-free flour", "9555064500016"))
                .thenReturn(List.of(buckwheatFlour));

        List<CatalogProduct> results = queryService.findSubstituteTagCandidates(source, wheatFloursProfile);

        assertEquals(1, results.size());
        assertEquals("8887501030697", results.getFirst().getBarcode());
    }

    @Test
    void findExpandedSubstituteCandidatesUnionsLabelsAndSiblingCategories() {
        CatalogProduct source = product(
                "0078895129779",
                "Soy sauces",
                "Water, salt, soybeans, wheat flour",
                "en:soy-sauces");
        CatalogProduct taggedGf = product(
                "4901515129889",
                "Soy sauces",
                "Water, Soybeans, Rice, Salt",
                "Gluten Free sauces");
        CatalogProduct labeledGf = product(
                "9343317000624",
                "Sauces",
                "Water, soybeans, salt",
                "en:sauces");
        labeledGf.setLabelsTags("en:no-gluten,en:vegan");
        CatalogProduct siblingSauce = product(
                "12456419",
                "Sauces",
                "Tomato",
                "Low sodium sauces");
        SubstituteDiscoveryProfile soySauceProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Soy sauces").orElseThrow();

        when(catalogProductRepository.findCandidatesByCategoryTag("Gluten Free sauces", "0078895129779"))
                .thenReturn(List.of(taggedGf));
        when(catalogProductRepository.findCandidatesByLabelTag("en:no-gluten", "0078895129779"))
                .thenReturn(List.of(labeledGf));
        when(catalogProductRepository.findCandidatesByLabelTag("en:certified-gluten-free", "0078895129779"))
                .thenReturn(List.of());
        when(catalogProductRepository.findCandidatesByCategories(
                soySauceProfile.siblingCategories(),
                "0078895129779"))
                .thenReturn(List.of(siblingSauce));

        List<CatalogProduct> results = queryService.findExpandedSubstituteCandidates(source, soySauceProfile);

        assertEquals(3, results.size());
        assertEquals("4901515129889", results.get(0).getBarcode());
        assertEquals("9343317000624", results.get(1).getBarcode());
        assertEquals("12456419", results.get(2).getBarcode());
    }

    @Test
    void findSubstituteTagCandidatesExcludesHoneyAndPeanutTracesForPeanutButterProfile() {
        CatalogProduct source = product(
                "0045300005409",
                "Crunchy peanut butters",
                "Roasted Peanuts, Sugar, Salt",
                "en:spreads,en:peanut-butters,en:crunchy-peanut-butters");
        CatalogProduct tahini = product(
                "8888536703136",
                "White tahini",
                "Organic sesame seeds",
                "en:oilseed-purees,en:cereal-butters,en:tahini");
        CatalogProduct honey = product(
                "5000119120656",
                "Honeys",
                "Honey",
                "en:spreads,en:honeys");
        CatalogProduct tracedSpread = product(
                "999",
                "Mixed nut butters",
                "Almonds, cashews",
                "en:oilseed-purees,en:nut-butters");
        tracedSpread.setTracesTags("en:peanuts");
        SubstituteDiscoveryProfile peanutButterProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Crunchy peanut butters").orElseThrow();

        when(catalogProductRepository.findCandidatesByCategoryTag("en:nut-butters", "0045300005409"))
                .thenReturn(List.of(tracedSpread));
        when(catalogProductRepository.findCandidatesByCategoryTag("en:tahini", "0045300005409"))
                .thenReturn(List.of(tahini));
        when(catalogProductRepository.findCandidatesByCategoryTag("en:cereal-butters", "0045300005409"))
                .thenReturn(List.of(tahini));
        when(catalogProductRepository.findCandidatesByCategoryTag("en:oilseed-purees", "0045300005409"))
                .thenReturn(List.of(tahini, honey, tracedSpread));

        List<CatalogProduct> results = queryService.findSubstituteTagCandidates(source, peanutButterProfile);

        assertEquals(1, results.size());
        assertEquals("8888536703136", results.getFirst().getBarcode());
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
