package com.canmakan.backend.product.recommendation.filter;

import com.canmakan.backend.product.recommendation.catalog.CatalogProduct;
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
    void rejectsMilkTracesForDairyIntoleranceEvenWhenVerdictIsSafe() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE)
        );
        CatalogProduct veganCoconut = catalogProduct(
                "0797776401192",
                "Ice cream tubs",
                "ice-creams-and-sorbets",
                null);
        veganCoconut.setTracesTags("en:milk,en:nuts");

        assertFalse(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.safe("ok", List.of()),
                veganCoconut));
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
    void acceptsWarningGlutenFreeFlourSubstituteForGlutenAvoidanceProfile() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID),
                new RestrictionRule("LOW_SUGAR", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE)
        );
        CatalogProduct buckwheatFlour = catalogProduct(
                "8887501030697",
                "Groceries",
                "No gluten, Gluten-free, Gluten-free flour",
                null);
        SafetyVerdict unresolved = SafetyVerdict.warning(
                "unresolved",
                List.of(new Finding("UNRESOLVED", "Buckwheat Flour", "could not be analysed")));

        assertTrue(filter.isAcceptableAlternative(rules, unresolved, buckwheatFlour));
    }

    @Test
    void rejectsWarningGlutenFreeFlourWhenGlutenFindingPresent() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );
        CatalogProduct taggedWheat = catalogProduct(
                "999",
                "Flours",
                "Gluten free flour",
                "en:gluten");
        SafetyVerdict glutenWarning = SafetyVerdict.warning(
                "gluten",
                List.of(new Finding("GLUTEN", "wheat", "gluten")));

        assertFalse(filter.isAcceptableAlternative(rules, glutenWarning, taggedWheat));
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

    @Test
    void rejectsGlutenFreeSpreadForWheatFlourSubstituteDiscovery() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );
        CatalogProduct tahini = catalogProduct(
                "8888536703136",
                "White tahini",
                "en:spreads,en:white-tahini,Gluten free Breakfast cereals",
                null);
        tahini.setProductName("Organic Tahini (Unhulled)");

        assertFalse(AlternativeCandidateFilter.isFlourSubstitute(tahini));
        assertTrue(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.safe("ok", List.of()),
                tahini));
    }

    @Test
    void acceptsCornFlourAsFlourSubstitute() {
        CatalogProduct cornFlour = catalogProduct(
                "8888030023662",
                "Corn starch",
                "en:corn-starch,en:gluten-free-flour",
                null);
        cornFlour.setProductName("Corn Flour");

        assertTrue(AlternativeCandidateFilter.isFlourSubstitute(cornFlour));
    }

    @Test
    void rejectsPeanutButterForPeanutAllergyEvenWhenVerdictIsSafe() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("PEANUT", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );
        CatalogProduct peanutButter = catalogProduct(
                "0045300005409",
                "Crunchy peanut butters",
                "en:spreads,en:peanut-butters,en:crunchy-peanut-butters",
                "en:peanuts");

        assertFalse(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.safe("ok", List.of()),
                peanutButter));
    }

    @Test
    void acceptsTahiniForPeanutAllergyProfile() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("PEANUT", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );
        CatalogProduct tahini = catalogProduct(
                "8888536703136",
                "White tahini",
                "en:oilseed-purees,en:cereal-butters,en:tahini",
                null);
        tahini.setTracesTags(null);

        assertTrue(AlternativeCandidateFilter.isPeanutFreeSpreadSubstitute(tahini));
        assertTrue(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.safe("ok", List.of()),
                tahini));
    }

    @Test
    void rejectsJamAsPeanutFreeSpreadSubstitute() {
        CatalogProduct strawberryJam = catalogProduct(
                "0044936350150",
                "Strawberry jams",
                "en:spreads,en:strawberry-jams",
                null);

        assertFalse(AlternativeCandidateFilter.isPeanutFreeSpreadSubstitute(strawberryJam));
    }

    @Test
    void rejectsHoneySpreadForPeanutAllergyProfile() {
        CatalogProduct honey = catalogProduct(
                "5000119120656",
                "Honeys",
                "en:spreads,en:honeys",
                null);

        assertFalse(AlternativeCandidateFilter.isPeanutFreeSpreadSubstitute(honey));
    }

    @Test
    void rejectsSpreadWithPeanutTracesAsPeanutFreeSpreadSubstitute() {
        CatalogProduct spread = catalogProduct(
                "999",
                "Mixed spreads",
                "en:spreads,en:chocolate-spreads",
                null);
        spread.setTracesTags("en:peanuts");

        assertFalse(AlternativeCandidateFilter.isPeanutFreeSpreadSubstitute(spread));
    }

    @Test
    void acceptsGlutenFreeBreadTagAsBreadSubstitute() {
        CatalogProduct gfBread = catalogProduct(
                "9339423009064",
                "Breads",
                "Gluten free bread,en:breads",
                null);

        assertTrue(AlternativeCandidateFilter.isGlutenFreeBreadSubstitute(gfBread));
    }

    @Test
    void rejectsFreshMilkAsGlutenFreeBreadSubstitute() {
        CatalogProduct soyaMilk = catalogProduct(
                "8888030019566",
                "Fresh milks",
                "en:fresh-milks",
                null);
        soyaMilk.setLabelsTags("en:no-gluten");

        assertFalse(AlternativeCandidateFilter.isGlutenFreeBreadSubstitute(soyaMilk));
    }

    @Test
    void acceptsBreadCategoryWithNoGlutenLabelAsGlutenFreeBreadSubstitute() {
        CatalogProduct gfSourdough = catalogProduct(
                "9339423009064",
                "Breads",
                "Gluten free bread,en:breads",
                null);
        gfSourdough.setLabelsTags("en:no-gluten");

        assertTrue(AlternativeCandidateFilter.isGlutenFreeBreadSubstitute(gfSourdough));
    }

    @Test
    void acceptsWarningGlutenFreeBreadWithoutIngredientsForGlutenProfile() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID),
                new RestrictionRule("LOW_SUGAR", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE)
        );
        CatalogProduct gfSourdough = catalogProduct(
                "0667380799179",
                "Breads",
                "Gluten free bread",
                null);
        gfSourdough.setProductName("Gluten Free Sourdough 7 Seed");
        gfSourdough.setIngredientsText(null);

        SafetyVerdict warning = SafetyVerdict.warning(
                "LOW_SUGAR: Sugar data is missing",
                List.of(new Finding("LOW_SUGAR", "nutrition", "Sugar data is missing")));

        assertTrue(filter.isAcceptableAlternative(rules, warning, gfSourdough));
    }

    @Test
    void acceptsGlutenFreeBreadTagCaseInsensitively() {
        CatalogProduct gfBread = catalogProduct(
                "0667380799179",
                "Breads",
                "gluten free bread",
                null);

        assertTrue(AlternativeCandidateFilter.isGlutenFreeBreadSubstitute(gfBread));
    }

    @Test
    void rejectsOatCerealMisTaggedAsGlutenFreeBread() {
        CatalogProduct oatCereal = catalogProduct(
                "8887143802515",
                "Breakfast cereals",
                "Gluten free bread,en:breakfast-cereals",
                null);
        oatCereal.setProductName("Honey Stars Oat Cereal");
        oatCereal.setIngredientsText("Oats, sugar, honey");

        assertFalse(AlternativeCandidateFilter.isGlutenFreeBreadSubstitute(oatCereal));
    }

    @Test
    void acceptsTaggedGlutenFreeBreakfastCerealWithoutOats() {
        CatalogProduct ancientGrains = catalogProduct(
                "9315090200706",
                "Breakfast cereals",
                "Gluten free Breakfast cereals,en:breakfast-cereals",
                null);
        ancientGrains.setProductName("Ancient grain flakes");
        ancientGrains.setIngredientsText("rice flour, yellow corn flour, sorghum flour, buckwheat flour");

        assertTrue(AlternativeCandidateFilter.isGlutenFreeBreakfastCerealSubstitute(ancientGrains));
    }

    @Test
    void rejectsOatGranolaTaggedAsGlutenFreeBreakfastCereal() {
        CatalogProduct oatGranola = catalogProduct(
                "8886478600698",
                "Breakfast cereals",
                "Gluten free Breakfast cereals,en:breakfast-cereals",
                null);
        oatGranola.setProductName("Dairy-free Soy Granola Blueberry Pistachio");
        oatGranola.setIngredientsText("Oats, Soy Pulps, Honey, Dried Blueberries");

        assertFalse(AlternativeCandidateFilter.isGlutenFreeBreakfastCerealSubstitute(oatGranola));
    }

    @Test
    void rejectsRolledOatsTaggedAsGlutenFreeBreakfastCereal() {
        CatalogProduct rolledOats = catalogProduct(
                "8887143802515",
                "Breakfast cereals",
                "Gluten free Breakfast cereals,en:breakfast-cereals",
                null);
        rolledOats.setProductName("Organic Rolled Oats");
        rolledOats.setIngredientsText("Organic Rolled Oats");

        assertFalse(AlternativeCandidateFilter.isGlutenFreeBreakfastCerealSubstitute(rolledOats));
    }

    @Test
    void rejectsFlourForGlutenFreeBreakfastCerealSubstituteDiscovery() {
        CatalogProduct cornFlour = catalogProduct(
                "8888030023662",
                "Corn starch",
                "en:corn-starch,en:gluten-free-flour",
                null);
        cornFlour.setProductName("Corn Flour");

        assertFalse(AlternativeCandidateFilter.isGlutenFreeBreakfastCerealSubstitute(cornFlour));
    }

    @Test
    void rejectsMisTaggedPotatoChipsAsGlutenFreeBreakfastCerealSubstitute() {
        CatalogProduct chips = catalogProduct(
                "7750526000895",
                "fr:chips-de-pommes-de-terre-classiques",
                "Gluten free Breakfast cereals,en:potato-crisps",
                null);
        chips.setProductName("Salted Potato Chips");
        chips.setIngredientsText("Potato, sunflower oil, salt");

        assertFalse(AlternativeCandidateFilter.isGlutenFreeBreakfastCerealSubstitute(chips));
    }

    @Test
    void acceptsWarningGlutenFreeBreakfastCerealWithoutGlutenFinding() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );
        CatalogProduct ancientGrains = catalogProduct(
                "9315090200706",
                "Breakfast cereals",
                "Gluten free Breakfast cereals,en:breakfast-cereals",
                null);
        ancientGrains.setProductName("Ancient grain flakes");
        ancientGrains.setIngredientsText("rice flour, sorghum flour, buckwheat flour");
        SafetyVerdict warning = SafetyVerdict.warning(
                "unresolved",
                List.of(new Finding("OTHER", "psyllium", "unknown ingredient")));

        assertTrue(filter.isAcceptableAlternative(rules, warning, ancientGrains));
    }

    @Test
    void acceptsReducedSaltSauceByProductNameAsLowSodiumSauceSubstitute() {
        CatalogProduct oysterSauce = catalogProduct(
                "0078895160482",
                "Sauces",
                "en:sauces",
                null);
        oysterSauce.setProductName("Reduced Salt Oyster Sauce");
        oysterSauce.setIngredientsText("Oyster extract, water, salt, sugar");

        assertTrue(AlternativeCandidateFilter.isLowSodiumSauceSubstitute(oysterSauce));
    }

    @Test
    void rejectsRegularTomatoSauceAsLowSodiumSauceSubstitute() {
        CatalogProduct tomatoSauce = catalogProduct(
                "9556001068163",
                "Groceries",
                "en:condiments,en:sauces,en:tomato-sauces,en:groceries",
                null);
        tomatoSauce.setProductName("Tomato sauce");
        tomatoSauce.setIngredientsText("Sugar, tomato paste, vinegar, salt");

        assertFalse(AlternativeCandidateFilter.isLowSodiumSauceSubstitute(tomatoSauce));
    }

    @Test
    void acceptsLowSodiumSauceForLowSodiumProfileEvenWhenVerdictIsWarning() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("LOW_SODIUM", RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE)
        );
        CatalogProduct oysterSauce = catalogProduct(
                "0078895160482",
                "Sauces",
                "en:sauces",
                null);
        oysterSauce.setProductName("Reduced Salt Oyster Sauce");
        oysterSauce.setIngredientsText("Oyster extract, water, salt, sugar");
        SafetyVerdict warning = SafetyVerdict.warning(
                "sodium",
                List.of(new Finding("LOW_SODIUM", "nutrition", "Sodium above limit")));

        assertTrue(filter.isAcceptableAlternative(rules, warning, oysterSauce));
    }

    @Test
    void rejectsDairySpreadForDairyIntoleranceEvenWhenVerdictIsSafe() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE)
        );
        CatalogProduct spread = catalogProduct(
                "8888010320453",
                "Dairies",
                "en:dairies",
                null);
        spread.setProductName("Luxury Spread");

        assertFalse(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.safe("ok", List.of()),
                spread));
    }

    @Test
    void acceptsCashewButterForDairyIntoleranceEvenWhenNameContainsButter() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("DAIRY", RestrictionCategory.ALLERGEN, RestrictionSeverity.INTOLERANCE)
        );
        CatalogProduct cashewButter = catalogProduct(
                "95539553",
                "Nut butters",
                "en:nut-butters,en:oilseed-purees",
                null);
        cashewButter.setProductName("Organic Cashew Butter");

        assertFalse(AlternativeCandidateFilter.hasDairyCatalogSignals(cashewButter));
        assertTrue(filter.isAcceptableAlternative(
                rules,
                SafetyVerdict.safe("ok", List.of()),
                cashewButter));
    }

    @Test
    void doesNotTreatPlantMilkWithMilkAllergenTagAsCowMilkCatalogProduct() {
        CatalogProduct soyaMilk = catalogProduct(
                "8888030019566",
                "Soy-based drinks",
                "en:milk-substitutes,en:soy-based-drinks,en:plant-based-milk-alternatives",
                "en:milk,en:soybeans");
        soyaMilk.setProductName("Hi-Calcium Fresh Soya Milk");

        assertTrue(AlternativeCandidateFilter.isPlantMilkSubstituteCandidate(soyaMilk));
        assertFalse(AlternativeCandidateFilter.isCowMilkCatalogProduct(soyaMilk));
    }

    @Test
    void isCowMilkCatalogProductReturnsFalseForNullCandidate() {
        assertFalse(AlternativeCandidateFilter.isCowMilkCatalogProduct(null));
    }

    @Test
    void isAcceptableAlternativeReturnsFalseWhenVerdictIsNull() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );

        assertFalse(filter.isAcceptableAlternative(rules, null, null));
    }

    @Test
    void rejectsGlutenWarningCandidateThatMatchesNoGlutenFreeSubstitute() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("GLUTEN", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );
        CatalogProduct plainSnack = catalogProduct(
                "1234567890123",
                "Snacks",
                "en:snacks",
                null);
        SafetyVerdict unresolvedWarning = SafetyVerdict.warning(
                "unresolved",
                List.of(new Finding("UNRESOLVED", "some additive", "could not be analysed")));

        assertFalse(filter.isAcceptableAlternative(rules, unresolvedWarning, plainSnack));
    }

    @Test
    void acceptsPeanutFreeSpreadSubstituteOnWarningVerdictWithoutPeanutFinding() {
        List<RestrictionRule> rules = List.of(
                new RestrictionRule("PEANUT", RestrictionCategory.ALLERGEN, RestrictionSeverity.STRICT_AVOID)
        );
        CatalogProduct tahini = catalogProduct(
                "8888536703136",
                "White tahini",
                "en:oilseed-purees,en:cereal-butters,en:tahini",
                null);
        tahini.setTracesTags(null);
        SafetyVerdict unresolvedWarning = SafetyVerdict.warning(
                "unresolved",
                List.of(new Finding("UNRESOLVED", "some additive", "could not be analysed")));

        assertTrue(filter.isAcceptableAlternative(rules, unresolvedWarning, tahini));
    }

    @Test
    void rejectsDairyMagnumAsIceCreamSubstitute() {
        CatalogProduct magnum = catalogProduct(
                "8712100857645",
                "Ice cream bars coated with chocolate",
                "en:ice-creams-and-sorbets,en:ice-cream-bars-coated-with-chocolate",
                null);
        magnum.setProductName("Magnum Glace Batonnet Mini Double Peanut Butter");
        magnum.setIngredientsText("LAIT écrémé réhydraté, sucre, LAIT en poudre entier");
        magnum.setLabelsTags("en:non-vegan");

        assertFalse(AlternativeCandidateFilter.isIceCreamSubstitute(magnum));
    }

    @Test
    void acceptsVeganSorbetAsIceCreamSubstitute() {
        CatalogProduct acai = catalogProduct(
                "0797776401178",
                "Ice creams and sorbets",
                "ice-creams-and-sorbets,en:ice-creams-and-sorbets",
                null);
        acai.setProductName("Acai berry");
        acai.setIngredientsText("Acai pulp, coconut milk, bananas");
        acai.setLabelsTags("en:vegan,en:vegetarian");

        assertTrue(AlternativeCandidateFilter.isIceCreamSubstitute(acai));
    }

    @Test
    void rejectsAlmondFlourWrapAsFlourSubstitute() {
        CatalogProduct wrap = catalogProduct(
                "8881300655204",
                null,
                "en:gluten-free-flour",
                null);
        wrap.setProductName("Gluten Free Vegan Almond Flour Tortilla Wraps");

        assertFalse(AlternativeCandidateFilter.isFlourSubstitute(wrap));
        assertFalse(AlternativeCandidateFilter.isGlutenFreeFlourSubstitute(wrap));
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
