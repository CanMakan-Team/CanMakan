package com.canmakan.backend.product.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.model.Nutrition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the approved per-100g nutrition rules and missing-data behavior.
 *
 * @author YangMaowei
 */
@DisplayName("UC3: NutritionChecker findings")
class NutritionCheckerTest {

    private final NutritionChecker checker = new NutritionChecker();

    @Test
    void supportsDietCategoryOnly() {
        assertTrue(checker.supports(RestrictionCategory.DIET));
        assertFalse(checker.supports(RestrictionCategory.ALLERGEN));
        assertFalse(checker.supports(RestrictionCategory.RELIGIOUS));
        assertFalse(checker.supports(null));
    }

    @ParameterizedTest
    @MethodSource("supportedNutrients")
    void nullNutritionWarnsToCheckLabel(String code, String nutrientName) {
        List<Finding> findings = check(code, null);
        assertEquals(1, findings.size());
        assertFindingCodeAndIngredient(findings.getFirst(), code, Finding.SUBJECT_NUTRITION);
        assertTrue(findings.getFirst().reason().contains(nutrientName));
        assertTrue(findings.getFirst().reason().contains("missing"));
        assertTrue(findings.getFirst().reason().contains("physical label"));
    }

    @ParameterizedTest
    @MethodSource("supportedNutrients")
    void nullFieldWarnsToCheckLabel(String code, String nutrientName) {
        List<Finding> findings = check(code, nutrition(code, null));
        assertEquals(1, findings.size());
        assertFindingCodeAndIngredient(findings.getFirst(), code, Finding.SUBJECT_NUTRITION);
        assertTrue(findings.getFirst().reason().contains("missing"));
        assertTrue(findings.getFirst().reason().contains("physical label"));
    }

    @ParameterizedTest
    @MethodSource("supportedNutrients")
    void confirmedZeroIsAccepted(String code, String nutrientName) {
        assertEquals(0, check(code, nutrition(code, BigDecimal.ZERO)).size());
    }

    @ParameterizedTest
    @MethodSource("supportedNutrients")
    void negativeValuesWarnToCheckLabel(String code, String nutrientName) {
        List<Finding> findings = check(code, nutrition(code, new BigDecimal("-0.01")));
        assertEquals(1, findings.size());
        assertFindingCodeAndIngredient(findings.getFirst(), code, Finding.SUBJECT_NUTRITION);
        assertTrue(findings.getFirst().reason().contains("invalid"));
        assertTrue(findings.getFirst().reason().contains("physical label"));
    }

    @ParameterizedTest
    @MethodSource("maximumThresholdCases")
    void belowAndEqualMaximumThresholdAreAccepted(
            String code,
            BigDecimal below,
            BigDecimal equal,
            BigDecimal above,
            String expectedReason
    ) {
        assertEquals(0, check(code, nutrition(code, below)).size());
        assertEquals(0, check(code, nutrition(code, equal)).size());
    }

    @ParameterizedTest
    @MethodSource("maximumThresholdCases")
    void aboveMaximumThresholdAddsExpectedFinding(
            String code,
            BigDecimal below,
            BigDecimal equal,
            BigDecimal above,
            String expectedReason
    ) {
        List<Finding> findings = check(code, nutrition(code, above));

        assertEquals(1, findings.size());
        assertFindingCodeAndIngredient(findings.getFirst(), code, Finding.SUBJECT_NUTRITION);
        assertEquals(expectedReason, findings.getFirst().reason());
    }

    @Test
    void positiveTransFatAddsThresholdFinding() {
        List<Finding> findings = check(
                "LOW_TRANS_FAT",
                nutrition("LOW_TRANS_FAT", new BigDecimal("0.001"))
        );

        assertEquals(1, findings.size());
        assertFindingCodeAndIngredient(findings.getFirst(), "LOW_TRANS_FAT", Finding.SUBJECT_NUTRITION);
        assertEquals(
                "Trans fat is 0.001 g per 100 g; the LOW_TRANS_FAT rule requires "
                        + "a confirmed value of 0 g per 100 g.",
                findings.getFirst().reason()
        );
    }

    @Test
    void negativeTransFatWarnsToCheckLabel() {
        List<Finding> findings = check(
                "LOW_TRANS_FAT",
                nutrition("LOW_TRANS_FAT", new BigDecimal("-0.001"))
        );

        assertEquals(1, findings.size());
        assertTrue(findings.getFirst().reason().contains("invalid"));
        assertTrue(findings.getFirst().reason().contains("physical label"));
    }

    @Test
    void ignoresPreferenceAndUnknownDietCodes() {
        Nutrition nutrition = new Nutrition(
                new BigDecimal("100"),
                new BigDecimal("100"),
                new BigDecimal("100"),
                new BigDecimal("100"),
                new BigDecimal("100"),
                new BigDecimal("100")
        );

        for (String code : List.of("VEGETARIAN", "VEGAN", "KETO")) {
            assertEquals(0, check(code, nutrition).size());
        }
    }

    @Test
    void preservesExistingFindings() {
        Finding existing = new Finding(null, null, "Existing finding.");
        List<Finding> findings = new ArrayList<>(List.of(existing));

        checker.check(
                rule("LOW_SUGAR"),
                product(nutrition("LOW_SUGAR", BigDecimal.ZERO)),
                findings
        );

        assertEquals(1, findings.size());
        assertFindingCodeAndIngredient(findings.getFirst(), null, null);
        assertEquals("Existing finding.", findings.getFirst().reason());
    }

    private List<Finding> check(String code, Nutrition nutrition) {
        List<Finding> findings = new ArrayList<>();
        checker.check(rule(code), product(nutrition), findings);
        return findings;
    }

    private static RestrictionRule rule(String code) {
        return new RestrictionRule(
                code,
                RestrictionCategory.DIET,
                RestrictionSeverity.INTOLERANCE
        );
    }

    private static ProductData product(Nutrition nutrition) {
        return new ProductData("123", List.of(), null, List.of(), nutrition, false);
    }

    private static Nutrition nutrition(String code, BigDecimal value) {
        return switch (code) {
            case "LOW_SUGAR" -> new Nutrition(value, null, null, null, null, null);
            case "LOW_FAT" -> new Nutrition(null, null, null, null, value, null);
            case "LOW_TRANS_FAT" -> new Nutrition(null, null, value, null, null, null);
            case "LOW_SODIUM" -> new Nutrition(null, value, null, null, null, null);
            default -> new Nutrition(value, null, null, null, null, null);
        };
    }

    private static Stream<Arguments> supportedNutrients() {
        return Stream.of(
                Arguments.of("LOW_SUGAR", "Sugar"),
                Arguments.of("LOW_FAT", "Total fat"),
                Arguments.of("LOW_TRANS_FAT", "Trans fat"),
                Arguments.of("LOW_SODIUM", "Sodium")
        );
    }

    private static Stream<Arguments> maximumThresholdCases() {
        return Stream.of(
                Arguments.of(
                        "LOW_SUGAR",
                        new BigDecimal("4.9"),
                        new BigDecimal("5.0"),
                        new BigDecimal("5.1"),
                        "Sugar is 5.1 g per 100 g, above the LOW_SUGAR limit of 5.0 g per 100 g."
                ),
                Arguments.of(
                        "LOW_FAT",
                        new BigDecimal("2.9"),
                        new BigDecimal("3.0"),
                        new BigDecimal("3.1"),
                        "Total fat is 3.1 g per 100 g, above the LOW_FAT limit of 3.0 g per 100 g."
                ),
                Arguments.of(
                        "LOW_SODIUM",
                        new BigDecimal("0.11"),
                        new BigDecimal("0.12"),
                        new BigDecimal("0.121"),
                        "Sodium is 121 mg per 100 g, above the LOW_SODIUM limit of 120 mg per 100 g."
                )
        );
    }

    private static void assertFindingCodeAndIngredient(
            Finding finding,
            String restrictionCode,
            String ingredientName
    ) {
        assertEquals(restrictionCode, finding.restrictionCode());
        assertEquals(ingredientName, finding.ingredientName());
    }
}
