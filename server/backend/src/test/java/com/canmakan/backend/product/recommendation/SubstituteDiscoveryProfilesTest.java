package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(profile.beverageTags().contains("en:oat-based-drinks"));
        assertTrue(profile.beverageTags().contains("en:soy-based-drinks"));
        assertTrue(profile.deprioritizeTags().contains("en:plant-based-creams-for-cooking"));
        assertTrue(profile.deprioritizeTags().contains("en:coconut-milks-and-creams"));
    }

    @Test
    void wheatFloursProfileContainsGlutenFreeFlourTag() {
        SubstituteDiscoveryProfile profile = profiles.forSourceCategory("Wheat flours").orElseThrow();

        assertEquals(List.of("en:gluten-free-flour"), profile.includeTags());
        assertTrue(profile.beverageTags().contains("en:corn-starch"));
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
        assertTrue(profile.beverageTags().isEmpty());
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
    }

    @Test
    void saucesProfileUsesLowSodiumSauceTags() {
        SubstituteDiscoveryProfile profile = profiles.forSourceCategory("Sauces").orElseThrow();

        assertEquals(List.of("Low sodium sauces", "Low sodium sauce"), profile.includeTags());
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
        for (String category : categories) {
            assertEquals(breads, profiles.forSourceCategory(category).orElseThrow());
        }
    }

    @Test
    void unknownSourceCategoryReturnsEmpty() {
        assertTrue(profiles.forSourceCategory("Snacks").isEmpty());
        assertTrue(profiles.forSourceCategory(null).isEmpty());
    }
}
