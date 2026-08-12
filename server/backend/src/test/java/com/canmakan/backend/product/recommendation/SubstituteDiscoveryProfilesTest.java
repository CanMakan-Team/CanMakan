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
    void unknownSourceCategoryReturnsEmpty() {
        assertTrue(profiles.forSourceCategory("Breakfast cereals").isEmpty());
        assertTrue(profiles.forSourceCategory(null).isEmpty());
    }
}
