package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.model.Nutrition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Applies the approved per-100g nutrition thresholds.
 *
 * @author YangMaowei
 */
// @Component
// public final class NutritionChecker implements RestrictionChecker {

//     private static final Set<String> SUPPORTED_CODES = Set.of(
//             "LOW_SUGAR", "LOW_FAT", "LOW_TRANS_FAT", "LOW_SODIUM"
//     );
//     private static final BigDecimal LOW_SUGAR_LIMIT = new BigDecimal("5.0");
//     private static final BigDecimal LOW_FAT_LIMIT = new BigDecimal("3.0");
//     private static final BigDecimal LOW_SODIUM_LIMIT = new BigDecimal("0.12");
//     private static final BigDecimal MILLIGRAMS_PER_GRAM = new BigDecimal("1000");

//     @Override
//     public boolean supports(RestrictionCategory category) {
//         return category == RestrictionCategory.DIET;
//     }

//     @Override
//     public void check(RestrictionRule rule, ProductData product, List<Finding> hits) {
//         Objects.requireNonNull(rule, "rule");
//         Objects.requireNonNull(product, "product");
//         Objects.requireNonNull(hits, "hits");

//         if (!supports(rule.category()) || !SUPPORTED_CODES.contains(rule.code())) {
//             return;
//         }

//         switch (rule.code()) {
//             case "LOW_SUGAR" -> checkMaximum(
//                     rule.code(), product.nutrition(), Nutrition::sugarsPer100g,
//                     "Sugar", LOW_SUGAR_LIMIT, "5.0", hits
//             );
//             case "LOW_FAT" -> checkMaximum(
//                     rule.code(), product.nutrition(), Nutrition::fatPer100g,
//                     "Total fat", LOW_FAT_LIMIT, "3.0", hits
//             );
//             case "LOW_TRANS_FAT" -> checkTransFat(product.nutrition(), hits);
//             case "LOW_SODIUM" -> checkSodium(product.nutrition(), hits);
//             default -> {
//             }
//         }
//     }

//     private void checkMaximum(
//             String code,
//             Nutrition nutrition,
//             Function<Nutrition, BigDecimal> valueExtractor,
//             String nutrientName,
//             BigDecimal limit,
//             String displayedLimit,
//             List<Finding> hits
//     ) {
//         BigDecimal value = nutrition == null ? null : valueExtractor.apply(nutrition);
//         if (addMissingOrInvalid(code, nutrientName, value, "g per 100 g", hits)) {
//             return;
//         }

//         if (value.compareTo(limit) > 0) {
//             hits.add(new Finding(
//                     code,
//                     null,
//                     nutrientName + " is " + format(value)
//                             + " g per 100 g, above the " + code + " limit of "
//                             + displayedLimit + " g per 100 g.",
//                     FindingType.THRESHOLD_EXCEEDED
//             ));
//         }
//     }

//     private void checkTransFat(Nutrition nutrition, List<Finding> hits) {
//         String code = "LOW_TRANS_FAT";
//         BigDecimal value = nutrition == null ? null : nutrition.transFatPer100g();
//         if (addMissingOrInvalid(code, "Trans fat", value, "g per 100 g", hits)) {
//             return;
//         }

//         if (value.compareTo(BigDecimal.ZERO) > 0) {
//             hits.add(new Finding(
//                     code,
//                     null,
//                     "Trans fat is " + format(value)
//                             + " g per 100 g; the LOW_TRANS_FAT rule requires a confirmed value of 0 g per 100 g.",
//                     FindingType.THRESHOLD_EXCEEDED
//             ));
//         }
//     }

//     private void checkSodium(Nutrition nutrition, List<Finding> hits) {
//         String code = "LOW_SODIUM";
//         BigDecimal value = nutrition == null ? null : nutrition.sodiumPer100g();
//         if (addMissingOrInvalid(code, "Sodium", value, "g per 100 g", hits)) {
//             return;
//         }

//         if (value.compareTo(LOW_SODIUM_LIMIT) > 0) {
//             BigDecimal milligrams = value.multiply(MILLIGRAMS_PER_GRAM);
//             hits.add(new Finding(
//                     code,
//                     null,
//                     "Sodium is " + format(milligrams)
//                             + " mg per 100 g, above the LOW_SODIUM limit of 120 mg per 100 g.",
//                     FindingType.THRESHOLD_EXCEEDED
//             ));
//         }
//     }

//     private boolean addMissingOrInvalid(
//             String code,
//             String nutrientName,
//             BigDecimal value,
//             String unit,
//             List<Finding> hits
//     ) {
//         if (value == null) {
//             hits.add(new Finding(
//                     code,
//                     null,
//                     nutrientName + " data is unavailable for the " + code + " restriction (" + unit + ").",
//                     FindingType.UNAVAILABLE_NUTRITION
//             ));
//             return true;
//         }
//         if (value.compareTo(BigDecimal.ZERO) < 0) {
//             hits.add(new Finding(
//                     code,
//                     null,
//                     nutrientName + " is " + format(value) + " " + unit
//                             + "; negative values are invalid for the " + code + " restriction.",
//                     FindingType.INVALID_NUTRITION
//             ));
//             return true;
//         }
//         return false;
//     }

//     private String format(BigDecimal value) {
//         return value.stripTrailingZeros().toPlainString();
//     }
// }
