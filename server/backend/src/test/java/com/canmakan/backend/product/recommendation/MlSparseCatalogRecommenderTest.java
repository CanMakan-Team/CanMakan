package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC5 Tier C: MlSparseCatalogRecommender")
class MlSparseCatalogRecommenderTest {

    @Mock
    private AlternativeProductQueryService queryService;

    private MlSparseCatalogRecommender recommender;

    @BeforeEach
    void setUp() {
        recommender = new MlSparseCatalogRecommender(
                queryService,
                new ProductFeatureEncoder(new ProductFeatureVectorStore(new com.fasterxml.jackson.databind.ObjectMapper(), "")));
    }

    @Test
    void infersMilkSubstituteTagsForSparseFreshMilkSource() {
        CatalogProduct source = new CatalogProduct();
        source.setBarcode("8888200602857");
        source.setProductName("Farmhouse Fresh Milk");
        source.setMainCategoryEn("Fresh milks");
        source.setIngredientsText("Fresh milks");

        CatalogProduct oatDrink = new CatalogProduct();
        oatDrink.setBarcode("7394376618253");
        oatDrink.setProductName("Oatly barista edition");

        when(queryService.findExpandedSubstituteCandidates(eq(source), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(oatDrink));

        List<CatalogProduct> discovered = recommender.discoverCandidates(source, null);

        assertEquals(1, discovered.size());
        assertEquals("7394376618253", discovered.getFirst().getBarcode());
        verify(queryService).findExpandedSubstituteCandidates(eq(source), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void marksFarmhouseFreshMilkAsSparse() {
        CatalogProduct source = new CatalogProduct();
        source.setBarcode("8888200602857");
        source.setMainCategoryEn("Fresh milks");
        source.setIngredientsText("Fresh milks");

        assertTrue(recommender.isSparseSource(source));
    }

    @Test
    void flourProfileSkipsBroadNoGlutenLabelExpansion() {
        CatalogProduct source = new CatalogProduct();
        source.setBarcode("4894514060287");
        source.setProductName("Wheat Flour");
        source.setMainCategoryEn("Wheat flours");
        source.setIngredientsText("Wheat flour");

        CatalogProduct tahini = new CatalogProduct();
        tahini.setBarcode("8888536703136");
        tahini.setProductName("Organic Tahini (Unhulled)");
        tahini.setLabelsTags("en:no-gluten");

        SubstituteDiscoveryProfile wheatFloursProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Wheat flours").orElseThrow();

        when(queryService.findExpandedSubstituteCandidates(eq(source), org.mockito.ArgumentMatchers.argThat(
                profile -> profile.labelTags().isEmpty()
                        && profile.siblingCategories().isEmpty()
                        && profile.includeTags().contains("en:corn-starch"))))
                .thenReturn(List.of(tahini));

        List<CatalogProduct> discovered = recommender.discoverCandidates(source, wheatFloursProfile);

        assertEquals(1, discovered.size());
        verify(queryService).findExpandedSubstituteCandidates(eq(source), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void breadProfileSkipsBroadNoGlutenLabelExpansion() {
        CatalogProduct source = new CatalogProduct();
        source.setBarcode("8888247111145");
        source.setProductName("Hi Calcium Milk Bread Plus");
        source.setMainCategoryEn("White breads");
        source.setIngredientsText("Wheat flour, skimmed milk powder, sugar");

        CatalogProduct soyaMilk = new CatalogProduct();
        soyaMilk.setBarcode("8888030019566");
        soyaMilk.setProductName("Hi-Calcium Fresh Soya Milk");
        soyaMilk.setLabelsTags("en:no-gluten");

        SubstituteDiscoveryProfile breadProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("White breads").orElseThrow();

        when(queryService.findExpandedSubstituteCandidates(eq(source), org.mockito.ArgumentMatchers.argThat(
                profile -> profile.labelTags().isEmpty()
                        && profile.siblingCategories().isEmpty()
                        && profile.includeTags().contains("Gluten free bread"))))
                .thenReturn(List.of(soyaMilk));

        List<CatalogProduct> discovered = recommender.discoverCandidates(source, breadProfile);

        assertEquals(1, discovered.size());
        verify(queryService).findExpandedSubstituteCandidates(eq(source), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void breakfastCerealProfileSkipsFlourInferenceAndBroadLabelExpansion() {
        CatalogProduct source = new CatalogProduct();
        source.setBarcode("4800361385046");
        source.setProductName("Honey Stars");
        source.setMainCategoryEn("Breakfast cereals");
        source.setCategoryTags("en:breakfast-cereals");
        source.setIngredientsText("Wholegrain Wheat, Corn Semolina, Sugar, Honey");

        CatalogProduct ancientGrains = new CatalogProduct();
        ancientGrains.setBarcode("9315090200706");
        ancientGrains.setProductName("Ancient grain flakes");
        ancientGrains.setCategoryTags("Gluten free Breakfast cereals");

        SubstituteDiscoveryProfile cerealProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Breakfast cereals").orElseThrow();

        when(queryService.findExpandedSubstituteCandidates(eq(source), org.mockito.ArgumentMatchers.argThat(
                profile -> profile.labelTags().isEmpty()
                        && profile.siblingCategories().isEmpty()
                        && profile.includeTags().equals(List.of("Gluten free Breakfast cereals"))
                        && !profile.includeTags().contains("en:gluten-free-flour"))))
                .thenReturn(List.of(ancientGrains));

        List<CatalogProduct> discovered = recommender.discoverCandidates(source, cerealProfile);

        assertEquals(1, discovered.size());
        verify(queryService).findExpandedSubstituteCandidates(eq(source), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void peanutButterProfileSkipsBroadSpreadInferenceAndLabelExpansion() {
        CatalogProduct source = new CatalogProduct();
        source.setBarcode("8888260007616");
        source.setProductName("Peanut Butter Crunchy");
        source.setMainCategoryEn("Peanut butters");
        source.setCategoryTags("en:spreads,en:peanut-butters");
        source.setIngredientsText("Roasted Peanuts, Sugar, Salt");

        CatalogProduct tahini = new CatalogProduct();
        tahini.setBarcode("8888536703136");
        tahini.setProductName("Organic Tahini (Unhulled)");
        tahini.setCategoryTags("en:oilseed-purees,en:cereal-butters,en:tahini");

        SubstituteDiscoveryProfile peanutProfile =
                new SubstituteDiscoveryProfiles().forSourceCategory("Peanut butters").orElseThrow();

        when(queryService.findExpandedSubstituteCandidates(eq(source), org.mockito.ArgumentMatchers.argThat(
                profile -> profile.labelTags().isEmpty()
                        && profile.siblingCategories().isEmpty()
                        && profile.includeTags().contains("en:nut-butters")
                        && !profile.includeTags().contains("en:spreads"))))
                .thenReturn(List.of(tahini));

        List<CatalogProduct> discovered = recommender.discoverCandidates(source, peanutProfile);

        assertEquals(1, discovered.size());
        verify(queryService).findExpandedSubstituteCandidates(eq(source), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsBarcodesAlreadyFoundByTierA() {
        CatalogProduct source = new CatalogProduct();
        source.setBarcode("0078895129779");
        source.setProductName("Soy Sauce");
        source.setMainCategoryEn("Soy sauces");
        source.setIngredientsText("Water, salt, soybeans, wheat flour");

        CatalogProduct alreadyFound = new CatalogProduct();
        alreadyFound.setBarcode("4901515129889");
        CatalogProduct extra = new CatalogProduct();
        extra.setBarcode("9343317000624");

        when(queryService.findExpandedSubstituteCandidates(eq(source), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(alreadyFound, extra, source));

        List<CatalogProduct> discovered = recommender.discoverCandidates(
                source,
                new SubstituteDiscoveryProfiles().forSourceCategory("Soy sauces").orElseThrow(),
                Set.of("4901515129889"));

        assertEquals(1, discovered.size());
        assertEquals("9343317000624", discovered.getFirst().getBarcode());
    }
}
