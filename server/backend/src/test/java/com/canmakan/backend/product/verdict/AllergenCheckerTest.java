package com.canmakan.backend.product.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests allergen matching against standardised product ingredients.
 *
 * @author YangMaowei
 */
@DisplayName("UC3: AllergenCheckerTest findings")
class AllergenCheckerTest {

    private static final String BANANA_MILK_BARCODE = "769828221591";

    private final AllergenChecker checker = new AllergenChecker();

    @Test
    void supportsOnlyAllergenCategory() {
        assertTrue(checker.supports(RestrictionCategory.ALLERGEN));
        assertFalse(checker.supports(RestrictionCategory.RELIGIOUS));
        assertFalse(checker.supports(RestrictionCategory.DIET));
        assertFalse(checker.supports(null));
    }

    @Test
    void addsFindingWhenRootAllergenMatchesRestrictionCode() {
        RestrictionRule rule = new RestrictionRule(
                "DAIRY",
                RestrictionCategory.ALLERGEN,
                RestrictionSeverity.STRICT_AVOID
        );
        Ingredient ingredient = new Ingredient(
                "Milk",
                "Milk Derivatives",
                "DAIRY",
                false
        );
        List<Finding> hits = new ArrayList<>();

        checker.check(rule, productWithIngredients(List.of(ingredient)), hits);

        assertEquals(
                List.of(new Finding(
                        "DAIRY",
                        "Milk",
                        "Milk matches the DAIRY restriction."
                )),
                hits
        );
    }

    @Test
    void addsFindingWhenLactoseIntolerantRuleMatchesDairyRootAllergen() {
        // Ingredients only carry a "DAIRY" root allergen tag; LACTOSE_INTOLERANT
        // is a separate selectable restriction (05_household_dietary_data.sql
        // id 16) that is treated as an alias of DAIRY so it still flags them.
        RestrictionRule rule = new RestrictionRule(
                "LACTOSE_INTOLERANT",
                RestrictionCategory.ALLERGEN,
                RestrictionSeverity.STRICT_AVOID
        );
        Ingredient ingredient = new Ingredient(
                "Milk",
                "Milk Derivatives",
                "DAIRY",
                false
        );
        List<Finding> hits = new ArrayList<>();

        checker.check(rule, productWithIngredients(List.of(ingredient)), hits);

        assertEquals(
                List.of(new Finding(
                        "LACTOSE_INTOLERANT",
                        "Milk",
                        "Milk matches the LACTOSE_INTOLERANT restriction."
                )),
                hits
        );
    }

    @Test
    void addsNoFindingWhenRootAllergenDoesNotMatch() {
        RestrictionRule rule = new RestrictionRule(
                "PEANUT",
                RestrictionCategory.ALLERGEN,
                RestrictionSeverity.STRICT_AVOID
        );
        Ingredient ingredient = new Ingredient(
                "Milk",
                "Milk Derivatives",
                "DAIRY",
                false
        );
        List<Finding> hits = new ArrayList<>();

        checker.check(rule, productWithIngredients(List.of(ingredient)), hits);

        assertTrue(hits.isEmpty());
    }

    @Test
    void usesExactCaseSensitiveAllergenMatching() {
        RestrictionRule rule = new RestrictionRule(
                "DAIRY",
                RestrictionCategory.ALLERGEN,
                RestrictionSeverity.STRICT_AVOID
        );
        Ingredient ingredient = new Ingredient(
                "Milk",
                "Milk Derivatives",
                "dairy",
                false
        );
        List<Finding> hits = new ArrayList<>();

        checker.check(rule, productWithIngredients(List.of(ingredient)), hits);

        assertTrue(hits.isEmpty());
    }

    @Test
    void addsOneFindingForEachMatchingIngredient() {
        RestrictionRule rule = new RestrictionRule(
                "DAIRY",
                RestrictionCategory.ALLERGEN,
                RestrictionSeverity.STRICT_AVOID
        );
        Ingredient milk = new Ingredient(
                "Milk",
                "Milk Derivatives",
                "DAIRY",
                false
        );
        Ingredient whey = new Ingredient(
                "Whey Powder",
                "Milk Derivatives",
                "DAIRY",
                false
        );
        List<Finding> hits = new ArrayList<>();

        checker.check(
                rule,
                productWithIngredients(List.of(milk, whey)),
                hits
        );

        assertEquals(
                List.of(
                        new Finding(
                                "DAIRY",
                                "Milk",
                                "Milk matches the DAIRY restriction."
                        ),
                        new Finding(
                                "DAIRY",
                                "Whey Powder",
                                "Whey Powder matches the DAIRY restriction."
                        )
                ),
                hits
        );
    }

    @Test
    void ignoresRulesFromUnsupportedCategories() {
        RestrictionRule rule = new RestrictionRule(
                "DAIRY",
                RestrictionCategory.RELIGIOUS,
                RestrictionSeverity.STRICT_AVOID
        );
        Ingredient ingredient = new Ingredient(
                "Milk",
                "Milk Derivatives",
                "DAIRY",
                false
        );
        List<Finding> hits = new ArrayList<>();

        checker.check(rule, productWithIngredients(List.of(ingredient)), hits);

        assertTrue(hits.isEmpty());
    }

    @Test
    void skipsNullIngredientsAndMissingRootAllergens() {
        RestrictionRule rule = new RestrictionRule(
                "DAIRY",
                RestrictionCategory.ALLERGEN,
                RestrictionSeverity.STRICT_AVOID
        );
        Ingredient missingRootAllergen = new Ingredient(
                "Unknown Ingredient",
                null,
                null,
                false
        );
        Ingredient milk = new Ingredient(
                "Milk",
                "Milk Derivatives",
                "DAIRY",
                false
        );
        List<Ingredient> ingredients = Arrays.asList(
                null,
                missingRootAllergen,
                milk
        );
        List<Finding> hits = new ArrayList<>();

        checker.check(rule, productWithIngredients(ingredients), hits);

        assertEquals(
                List.of(new Finding(
                        "DAIRY",
                        "Milk",
                        "Milk matches the DAIRY restriction."
                )),
                hits
        );
    }

    @Test
    void appendsMatchesWithoutRemovingExistingFindings() {
        RestrictionRule rule = new RestrictionRule(
                "DAIRY",
                RestrictionCategory.ALLERGEN,
                RestrictionSeverity.STRICT_AVOID
        );
        Ingredient ingredient = new Ingredient(
                "Milk",
                "Milk Derivatives",
                "DAIRY",
                false
        );
        Finding existing = new Finding(
                null,
                null,
                "Existing data-quality finding."
        );
        List<Finding> hits = new ArrayList<>();
        hits.add(existing);

        checker.check(rule, productWithIngredients(List.of(ingredient)), hits);

        assertEquals(
                List.of(
                        existing,
                        new Finding(
                                "DAIRY",
                                "Milk",
                                "Milk matches the DAIRY restriction."
                        )
                ),
                hits
        );
    }

    @Test
    void rejectsNullRequiredArguments() {
        RestrictionRule rule = new RestrictionRule(
                "DAIRY",
                RestrictionCategory.ALLERGEN,
                RestrictionSeverity.STRICT_AVOID
        );
        ProductData product = productWithIngredients(List.of());
        List<Finding> hits = new ArrayList<>();

        assertThrows(
                NullPointerException.class,
                () -> checker.check(null, product, hits)
        );
        assertThrows(
                NullPointerException.class,
                () -> checker.check(rule, null, hits)
        );
        assertThrows(
                NullPointerException.class,
                () -> checker.check(rule, product, null)
        );
    }

    private static ProductData productWithIngredients(
            List<Ingredient> ingredients
    ) {
        return new ProductData(
                BANANA_MILK_BARCODE,
                ingredients,
                null,
                List.of(),
                null,
                true
        );
    }
}
