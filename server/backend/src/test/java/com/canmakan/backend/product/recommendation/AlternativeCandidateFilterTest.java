package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.verdict.Finding;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.RestrictionSeverity;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UC5: AlternativeCandidateFilter")
class AlternativeCandidateFilterTest {

    private AlternativeCandidateFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AlternativeCandidateFilter();
    }

    @Test
    void requiresSafeWhenProfileHasNoIntoleranceRules() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );

        assertTrue(filter.isAcceptableAlternative(rules, SafetyVerdict.safe("ok", List.of()), null));
        assertFalse(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.warning("gluten", List.of(new Finding("GLUTEN", "wheat", "gluten"))),
                null));
        assertFalse(filter.isAcceptableAlternative(rules, SafetyVerdict.unsafe("gluten", List.of()), null));
    }

    @Test
    void acceptsWarningWithoutIntoleranceHitWhenProfileHasIntolerance() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE),
                new RestrictionRule("PEANUT", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );
        CatalogProduct oatDrink = catalogProduct(
                "7394376618253",
                "Oat-based drinks",
                "en:oat-based-drinks,en:milk-substitutes",
                null);

        SafetyVerdict oatDrinkWarning = SafetyVerdict.warning(
                "unresolved",
                List.of(new Finding("UNRESOLVED", "dipotassium phosphate", "could not be analysed")));

        assertTrue(filter.isAcceptableAlternative(rules, oatDrinkWarning, oatDrink));
    }

    @Test
    void rejectsCandidateThatTriggersIntoleranceRule() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE)
        );

        SafetyVerdict dairyWarning = SafetyVerdict.warning(
                "dairy",
                List.of(new Finding("DAIRY", "milk", "milk matches DAIRY restriction.")));

        assertFalse(filter.isAcceptableAlternative(rules, dairyWarning, null));
    }

    @Test
    void rejectsFreshMilkCategoryForDairyIntoleranceEvenWhenVerdictIsSafe() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE)
        );
        CatalogProduct magnolia = catalogProduct(
                "8888200132118",
                "Fresh milks",
                "en:fresh-milks,en:milks",
                null);

        assertFalse(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.safe("ok", List.of()),
                magnolia));
    }

    @Test
    void rejectsWholeMilkCategoryForDairyIntoleranceEvenWhenVerdictIsSafe() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE)
        );
        CatalogProduct meadows = catalogProduct(
                "4894514034424",
                "Whole milks",
                "en:whole-milks",
                null);

        assertFalse(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.safe("ok", List.of()),
                meadows));
    }

    @Test
    void rejectsDeclaredMilkAllergenForDairyIntoleranceEvenWhenVerdictIsSafe() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE)
        );
        CatalogProduct hokkaido = catalogProduct(
                "4908013129717",
                "Fresh milks",
                "en:fresh-milks",
                "en:milk");

        assertFalse(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.safe("ok", List.of()),
                hokkaido));
    }

    @Test
    void rejectsWheatFlourCategoryForGlutenProfileEvenWhenVerdictIsSafe() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );
        CatalogProduct plainFlour = catalogProduct(
                "4894514060287",
                "Wheat flours",
                "en:wheat-flours,en:cereal-flours",
                "en:gluten");

        assertFalse(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.safe("ok", List.of()),
                plainFlour));
    }

    @Test
    void rejectsWhiteWheatFlourCategoryForGlutenProfileEvenWhenVerdictIsSafe() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );
        CatalogProduct breadFlour = catalogProduct(
                "8886350000042",
                "White wheat flours",
                "en:white-wheat-flours,en:bread-flours",
                "en:gluten");

        assertFalse(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.safe("ok", List.of()),
                breadFlour));
    }

    @Test
    void rejectsUnsafeCandidatesEvenWithIntoleranceProfile() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("PEANUT", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID),
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE)
        );

        assertFalse(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.unsafe("peanut", List.of(new Finding("PEANUT", "peanut", "peanut"))),
                null));
    }

    private static CatalogProduct catalogProduct(
            String barcode,
            String category,
            String categoryTags,
            String allergens) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName("Milk product");
        product.setMainCategoryEn(category);
        product.setCategoryTags(categoryTags);
        product.setAllergens(allergens);
        product.setIngredientsText("fresh milk");
        return product;
    }
}
