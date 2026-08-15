package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UC5: SubstituteDiscoveryProfiles")
class SubstituteDiscoveryProfilesTest {

    private final SubstituteDiscoveryProfiles profiles = new SubstituteDiscoveryProfiles();

    @Test
    void freshMilksProfileContainsExpectedTagGroups() {
        SubstituteDiscoveryProfile profile = profiles.forSourceCategory("Fresh milks").orElseThrow();

        assertTrue(profile.includeTags().contains("en:milk-substitutes"));
        assertTrue(profile.includeTags().contains("en:dairy-substitutes"));
        assertTrue(profile.includeTags().contains("en:oat-based-drinks"));
        assertTrue(profile.includeTags().contains("en:soy-based-drinks"));
        assertTrue(profile.secondaryIncludeTags().contains("en:oat-based-drinks"));
        assertTrue(profile.secondaryIncludeTags().contains("en:soy-based-drinks"));
        assertTrue(profile.deprioritizeTags().contains("en:plant-based-creams-for-cooking"));
        assertTrue(profile.deprioritizeTags().contains("en:coconut-milks-and-creams"));
    }

    @Test
    void wheatFloursProfileContainsGlutenFreeFlourTag() {
        SubstituteDiscoveryProfile profile = profiles.forSourceCategory("Wheat flours").orElseThrow();

        assertTrue(profile.includeTags().contains("en:gluten-free-flour"));
        assertTrue(profile.includeTags().contains("Gluten free flour"));
        assertTrue(profile.includeTags().contains("Gluten-free flour"));
        assertTrue(profile.secondaryIncludeTags().contains("en:corn-starch"));
        assertTrue(profile.deprioritizeTags().contains("en:oat-flour"));
    }

    @Test
    void whiteWheatFloursUsesSameProfileAsWheatFlours() {
        assertEquals(
                profiles.forSourceCategory("Wheat flours"),
                profiles.forSourceCategory("White wheat flours"));
    }

    @Test
    void cowMilkCategoriesShareFreshMilksSubstituteProfile() {
        SubstituteDiscoveryProfile freshMilks = profiles.forSourceCategory("Fresh milks").orElseThrow();
        List<String> milkCategories = List.of(
                "Whole milk UHT",
                "UHT milks",
                "Whole milks",
                "Skimmed milks",
                "Milks",
                "Pasteurised milks",
                "Whole pasteurised milks",
                "Cow milks",
                "UHT Skimmed milks",
                "Strawberry milks",
                "Flavoured milks",
                "Chocolate milks",
                "Homogenized milks"
        );

        for (String category : milkCategories) {
            assertEquals(
                    freshMilks,
                    profiles.forSourceCategory(category).orElseThrow(),
                    "Expected substitute profile for " + category);
        }
    }

    @Test
    void breakfastCerealsProfileUsesGlutenFreeBreakfastCerealsTag() {
        SubstituteDiscoveryProfile profile = profiles.forSourceCategory("Breakfast cereals").orElseThrow();

        assertEquals(List.of("Gluten free Breakfast cereals"), profile.includeTags());
        assertTrue(profiles.isBreakfastCerealSubstituteDiscovery(profile));
        assertEquals(List.of("en:no-gluten", "en:certified-gluten-free"), profile.labelTags());
        assertEquals(List.of("Breakfast cereals"), profile.siblingCategories());
        assertTrue(profile.secondaryIncludeTags().isEmpty());
        assertTrue(profile.deprioritizeTags().isEmpty());
    }

    @Test
    void iceCreamCategoriesShareSorbetSubstituteProfile() {
        SubstituteDiscoveryProfile iceCreams = profiles.forSourceCategory("Ice creams").orElseThrow();
        List<String> categories = List.of(
                "Ice cream cones",
                "Ice cream bars",
                "Ice cream tubs",
                "Ice creams"
        );

        assertEquals(
                List.of("ice-creams-and-sorbets", "en:ice-creams-and-sorbets"),
                iceCreams.includeTags());
        for (String category : categories) {
            assertEquals(iceCreams, profiles.forSourceCategory(category).orElseThrow());
        }
    }

    @Test
    void soySaucesProfileUsesGlutenFreeSaucesTag() {
        SubstituteDiscoveryProfile profile = profiles.forSourceCategory("Soy sauces").orElseThrow();

        assertEquals(List.of("Gluten Free sauces"), profile.includeTags());
        assertEquals(List.of("en:no-gluten", "en:certified-gluten-free"), profile.labelTags());
        assertEquals(List.of("Sauces", "Soy sauces"), profile.siblingCategories());
    }

    @Test
    void saucesProfileUsesLowSodiumSauceTags() {
        SubstituteDiscoveryProfile profile = profiles.forSourceCategory("Sauces").orElseThrow();

        assertEquals(List.of("Low sodium sauces", "Low sodium sauce"), profile.includeTags());
        assertTrue(profile.labelTags().contains("en:low-salt"));
        assertTrue(profile.labelTags().contains("en:reduced-salt"));
        assertEquals(List.of("Sauces", "Soy sauces"), profile.siblingCategories());
        assertTrue(profiles.isLowSodiumSauceSubstituteDiscovery(profile));
    }

    @Test
    void fishSauceTaggedGroceriesResolvesLowSodiumSauceProfile() {
        CatalogProduct fishSauce = new CatalogProduct();
        fishSauce.setBarcode("8850581172007");
        fishSauce.setMainCategoryEn("Groceries");
        fishSauce.setCategoryTags("en:condiments,en:sauces,en:nuoc-mam-sauce,en:groceries");
        fishSauce.setIngredientsText("Anchovies, Salt, Sugar");

        SubstituteDiscoveryProfile profile = profiles.forSourceProduct(fishSauce).orElseThrow();

        assertEquals(List.of("Low sodium sauces", "Low sodium sauce"), profile.includeTags());
        assertTrue(SubstituteDiscoveryProfiles.isSauceSource(fishSauce));
    }

    @Test
    void peanutButterCategoriesShareNutButterSubstituteProfile() {
        SubstituteDiscoveryProfile peanutButters = profiles.forSourceCategory("Peanut butters").orElseThrow();

        assertEquals(List.of(
                "en:nut-butters",
                "en:tahini",
                "en:cereal-butters",
                "en:oilseed-purees"), peanutButters.includeTags());
        assertEquals(List.of(
                "en:nut-butters",
                "en:tahini",
                "en:cereal-butters"), peanutButters.secondaryIncludeTags());
        assertEquals(List.of("en:honeys"), peanutButters.excludeCategoryTags());
        assertEquals(List.of("en:peanuts"), peanutButters.excludeTracesTags());
        assertEquals(peanutButters, profiles.forSourceCategory("Crunchy peanut butters").orElseThrow());
        assertTrue(profiles.isPeanutSpreadSubstituteDiscovery(peanutButters));
    }

    @Test
    void breadCategoriesShareGlutenFreeBreadSubstituteProfile() {
        SubstituteDiscoveryProfile breads = profiles.forSourceCategory("Breads").orElseThrow();
        List<String> categories = List.of(
                "Sliced breads",
                "Breads",
                "Wholemeal breads",
                "Multigrain-bread",
                "Wheat flatbreads",
                "Wholemeal sliced breads",
                "Whole-meal-bread",
                "White breads",
                "Multigrain sliced breads"
        );

        assertEquals(List.of("Gluten free bread"), breads.includeTags());
        assertTrue(profiles.isBreadSubstituteDiscovery(breads));
        for (String category : categories) {
            assertEquals(breads, profiles.forSourceCategory(category).orElseThrow());
        }
        assertEquals(breads, profiles.forSourceCategory("breads").orElseThrow());
    }

    @Test
    void lowercaseBreadCategoryResolvesViaForSourceProduct() {
        CatalogProduct milkFlavorBread = new CatalogProduct();
        milkFlavorBread.setBarcode("6933352827077");
        milkFlavorBread.setProductName("Milk Flavor Bread");
        milkFlavorBread.setMainCategoryEn("breads");
        milkFlavorBread.setCategoryTags("en:breads");
        milkFlavorBread.setIngredientsText("wheat flour, sugar, yeast");

        SubstituteDiscoveryProfile profile = profiles.forSourceProduct(milkFlavorBread).orElseThrow();

        assertEquals(List.of("Gluten free bread"), profile.includeTags());
        assertTrue(profiles.isBreadSubstituteDiscovery(profile));
    }

    @Test
    void chocolateIceCreamTubsUsesIceCreamSubstituteProfile() {
        CatalogProduct chocolateTub = new CatalogProduct();
        chocolateTub.setBarcode("9414263008108");
        chocolateTub.setMainCategoryEn("Chocolate ice cream tubs");
        chocolateTub.setCategoryTags(
                "en:desserts,en:frozen-foods,en:frozen-desserts,en:ice-creams-and-sorbets,"
                        + "en:ice-creams,en:ice-cream-tubs,en:chocolate-ice-cream-tubs");
        chocolateTub.setIngredientsText("Milk, Cream, Liquid Sugar, Cocoa Powder");

        SubstituteDiscoveryProfile profile = profiles.forSourceProduct(chocolateTub).orElseThrow();

        assertEquals(
                List.of("ice-creams-and-sorbets", "en:ice-creams-and-sorbets"),
                profile.includeTags());
        assertTrue(profiles.isIceCreamSubstituteDiscovery(profile));
    }

    @Test
    void iceCreamEncoderDoesNotInferMilkSubstitutesFromDairyIngredients() {
        CatalogProduct chocolateTub = new CatalogProduct();
        chocolateTub.setProductName("New Zealand Chocolate Ecstasy Ice Cream");
        chocolateTub.setMainCategoryEn("Chocolate ice cream tubs");
        chocolateTub.setCategoryTags(
                "en:ice-creams-and-sorbets,en:ice-creams,en:chocolate-ice-cream-tubs");
        chocolateTub.setIngredientsText("Milk, Cream, Liquid Sugar, Cocoa Powder");

        ProductFeatureEncoder encoder =
                new ProductFeatureEncoder(new ProductFeatureVectorStore(new com.fasterxml.jackson.databind.ObjectMapper(), ""));

        assertTrue(encoder.inferSubstituteTags(chocolateTub).contains("ice-creams-and-sorbets"));
        assertTrue(encoder.inferSubstituteTags(chocolateTub).stream()
                .noneMatch(tag -> tag.contains("milk-substitute") || tag.contains("dairy-substitute")));
    }

    @Test
    void breadEncoderDoesNotInferMilkSubstitutesFromMilkBreadName() {
        CatalogProduct milkBread = new CatalogProduct();
        milkBread.setProductName("Hi Calcium Milk Bread Plus");
        milkBread.setMainCategoryEn("White breads");
        milkBread.setCategoryTags("en:breads,en:white-breads");
        milkBread.setIngredientsText(
                "Wheat flour, skimmed milk powder, sugar, Baker yeast");

        ProductFeatureEncoder encoder =
                new ProductFeatureEncoder(new ProductFeatureVectorStore(new com.fasterxml.jackson.databind.ObjectMapper(), ""));

        assertTrue(encoder.inferSubstituteTags(milkBread).contains("Gluten free bread"));
        assertTrue(encoder.inferSubstituteTags(milkBread).stream()
                .noneMatch(tag -> tag.contains("milk-substitute") || tag.contains("dairy-substitute")));
    }

    @Test
    void breakfastCerealEncoderDoesNotInferFlourSubstitutesFromWheatCereal() {
        CatalogProduct honeyStars = new CatalogProduct();
        honeyStars.setProductName("Honey Stars");
        honeyStars.setMainCategoryEn("Breakfast cereals");
        honeyStars.setCategoryTags("en:breakfast-cereals");
        honeyStars.setIngredientsText("Wholegrain Wheat, Corn Semolina, Sugar, Honey");

        ProductFeatureEncoder encoder =
                new ProductFeatureEncoder(new ProductFeatureVectorStore(new com.fasterxml.jackson.databind.ObjectMapper(), ""));

        assertTrue(encoder.inferSubstituteTags(honeyStars).contains("Gluten free Breakfast cereals"));
        assertTrue(encoder.inferSubstituteTags(honeyStars).stream()
                .noneMatch(tag -> tag.contains("gluten-free-flour") || tag.contains("Gluten free flour")));
    }

    @Test
    void unknownSourceCategoryReturnsEmpty() {
        assertTrue(profiles.forSourceCategory("Snacks").isEmpty());
        assertTrue(profiles.forSourceCategory(null).isEmpty());
    }

    @Test
    void dairiesFreshMilkResolvesToPlantMilkSubstituteProfile() {
        CatalogProduct magnoliaMilk = new CatalogProduct();
        magnoliaMilk.setBarcode("8888200602734");
        magnoliaMilk.setProductName("100% Fresh Milk");
        magnoliaMilk.setMainCategoryEn("Dairies");
        magnoliaMilk.setCategoryTags("en:dairies");
        magnoliaMilk.setIngredientsText("Fresh Milk 100%");

        SubstituteDiscoveryProfile profile = profiles.forSourceProduct(magnoliaMilk).orElseThrow();

        assertEquals(
                profiles.forSourceCategory("Fresh milks").orElseThrow(),
                profile);
        assertTrue(SubstituteDiscoveryProfiles.isCowMilkSource(magnoliaMilk));
    }

    @Test
    void dairiesSpreadIsNotCowMilkSource() {
        CatalogProduct spread = new CatalogProduct();
        spread.setBarcode("8888010320453");
        spread.setProductName("Luxury Spread");
        spread.setMainCategoryEn("Dairies");
        spread.setCategoryTags("en:dairies");

        assertTrue(profiles.forSourceProduct(spread).isEmpty());
        assertFalse(SubstituteDiscoveryProfiles.isCowMilkSource(spread));
    }

    @Test
    void uhtMilkMiscategorizedAsDairiesIsCowMilkSource() {
        CatalogProduct uhtMilk = new CatalogProduct();
        uhtMilk.setBarcode("8888200601416");
        uhtMilk.setProductName("UHT Low Fat Milk");
        uhtMilk.setMainCategoryEn("Dairies");
        uhtMilk.setCategoryTags("en:dairies");
        uhtMilk.setAllergens("en:milk");

        assertTrue(SubstituteDiscoveryProfiles.isCowMilkSource(uhtMilk));
        assertEquals(
                profiles.forSourceCategory("Fresh milks").orElseThrow(),
                profiles.forSourceProduct(uhtMilk).orElseThrow());
    }
}
