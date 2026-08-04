package com.canmakan.backend.product.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.model.Nutrition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the approved per-100g nutrition rules and missing-data behavior.
 *
 * @author YangMaowei
 */
// class NutritionCheckerTest {

//     private final NutritionChecker checker = new NutritionChecker();

//     @Test
//     void supportsDietCategoryOnly() {
//         assertTrue(checker.supports(RestrictionCategory.DIET));
//         assertTrue(!checker.supports(RestrictionCategory.ALLERGEN));
//         assertTrue(!checker.supports(RestrictionCategory.RELIGIOUS));
//         assertTrue(!checker.supports(null));
//     }

//     @ParameterizedTest
//     @MethodSource("supportedCodes")
//     void nullNutritionProducesUnavailableFinding(String code) {
//         List<Finding> findings = check(code, null);

//         assertEquals(1, findings.size());
//         assertEquals(FindingType.UNAVAILABLE_NUTRITION, findings.getFirst().type());
//         assertTrue(findings.getFirst().reason().contains(code));
//         assertTrue(findings.getFirst().reason().contains("per 100 g"));
//     }

//     @ParameterizedTest
//     @MethodSource("supportedCodes")
//     void nullFieldProducesUnavailableFinding(String code) {
//         List<Finding> findings = check(code, nutrition(code, null));

//         assertEquals(FindingType.UNAVAILABLE_NUTRITION, findings.getFirst().type());
//     }

//     @ParameterizedTest
//     @MethodSource("supportedCodes")
//     void confirmedZeroIsAccepted(String code) {
//         assertTrue(check(code, nutrition(code, BigDecimal.ZERO)).isEmpty());
//     }

//     @ParameterizedTest
//     @MethodSource("negativeCases")
//     void negativeValuesProduceInvalidDataWarning(String code, String unit) {
//         List<Finding> findings = check(code, nutrition(code, new BigDecimal("-0.01")));

//         assertEquals(FindingType.INVALID_NUTRITION, findings.getFirst().type());
//         assertTrue(findings.getFirst().reason().contains("-0.01 " + unit));
//         assertTrue(findings.getFirst().reason().contains(code));
//     }

//     @ParameterizedTest
//     @MethodSource("thresholdCases")
//     void belowAndEqualThresholdAreAccepted(
//             String code,
//             BigDecimal below,
//             BigDecimal equal,
//             BigDecimal above,
//             String expectedReason
//     ) {
//         assertTrue(check(code, nutrition(code, below)).isEmpty());
//         assertTrue(check(code, nutrition(code, equal)).isEmpty());
//     }

//     @ParameterizedTest
//     @MethodSource("thresholdCases")
//     void aboveThresholdProducesExpectedFinding(
//             String code,
//             BigDecimal below,
//             BigDecimal equal,
//             BigDecimal above,
//             String expectedReason
//     ) {
//         List<Finding> findings = check(code, nutrition(code, above));

//         assertEquals(1, findings.size());
//         assertEquals(FindingType.THRESHOLD_EXCEEDED, findings.getFirst().type());
//         assertEquals(expectedReason, findings.getFirst().reason());
//     }

//     @Test
//     void transFatHasNoValidValueBelowZero() {
//         List<Finding> findings = check(
//                 "LOW_TRANS_FAT",
//                 nutrition("LOW_TRANS_FAT", new BigDecimal("-0.001"))
//         );

//         assertEquals(FindingType.INVALID_NUTRITION, findings.getFirst().type());
//     }

//     @Test
//     void ignoresUnsupportedAndPreferenceDietCodes() {
//         Nutrition nutrition = nutrition("LOW_SUGAR", new BigDecimal("100"));

//         for (String code : List.of("VEGETARIAN", "VEGAN", "KETO")) {
//             assertTrue(check(code, nutrition).isEmpty());
//         }
//     }

//     @Test
//     void preservesExistingFindings() {
//         Finding existing = new Finding(null, null, "Existing finding.", FindingType.INCOMPLETE_DATA);
//         List<Finding> findings = new ArrayList<>(List.of(existing));
//         checker.check(
//                 rule("LOW_SUGAR"),
//                 product(nutrition("LOW_SUGAR", BigDecimal.ZERO)),
//                 findings
//         );

//         assertEquals(List.of(existing), findings);
//     }

//     private List<Finding> check(String code, Nutrition nutrition) {
//         List<Finding> findings = new ArrayList<>();
//         checker.check(rule(code), product(nutrition), findings);
//         return findings;
//     }

//     private static RestrictionRule rule(String code) {
//         return new RestrictionRule(code, RestrictionCategory.DIET, RestrictionSeverity.PREFERENCE);
//     }

//     private static ProductData product(Nutrition nutrition) {
//         return new ProductData("123", List.of(), null, List.of(), nutrition, false);
//     }

//     private static Nutrition nutrition(String code, BigDecimal value) {
//         return switch (code) {
//             case "LOW_SUGAR" -> new Nutrition(value, null, null, null, null, null);
//             case "LOW_FAT" -> new Nutrition(null, null, null, null, value, null);
//             case "LOW_TRANS_FAT" -> new Nutrition(null, null, value, null, null, null);
//             case "LOW_SODIUM" -> new Nutrition(null, value, null, null, null, null);
//             default -> new Nutrition(value, null, null, null, null, null);
//         };
//     }

//     private static Stream<String> supportedCodes() {
//         return Stream.of("LOW_SUGAR", "LOW_FAT", "LOW_TRANS_FAT", "LOW_SODIUM");
//     }

//     private static Stream<Arguments> negativeCases() {
//         return Stream.of(
//                 Arguments.of("LOW_SUGAR", "g per 100 g"),
//                 Arguments.of("LOW_FAT", "g per 100 g"),
//                 Arguments.of("LOW_TRANS_FAT", "g per 100 g"),
//                 Arguments.of("LOW_SODIUM", "g per 100 g")
//         );
//     }

//     private static Stream<Arguments> thresholdCases() {
//         return Stream.of(
//                 Arguments.of(
//                         "LOW_SUGAR",
//                         new BigDecimal("4.9"),
//                         new BigDecimal("5.0"),
//                         new BigDecimal("5.1"),
//                         "Sugar is 5.1 g per 100 g, above the LOW_SUGAR limit of 5.0 g per 100 g."
//                 ),
//                 Arguments.of(
//                         "LOW_FAT",
//                         new BigDecimal("2.9"),
//                         new BigDecimal("3.0"),
//                         new BigDecimal("3.1"),
//                         "Total fat is 3.1 g per 100 g, above the LOW_FAT limit of 3.0 g per 100 g."
//                 ),
//                 Arguments.of(
//                         "LOW_SODIUM",
//                         new BigDecimal("0.11"),
//                         new BigDecimal("0.12"),
//                         new BigDecimal("0.121"),
//                         "Sodium is 121 mg per 100 g, above the LOW_SODIUM limit of 120 mg per 100 g."
//                 )
//         );
//     }
// }
