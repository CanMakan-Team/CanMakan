package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.model.Nutrition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Applies the approved per-100g nutrition thresholds.
 *
 * @author YangMaowei
 * @author XieHuayuan
 */
@Component
public final class NutritionChecker implements RestrictionChecker {

    private static final Set<String> SUPPORTED_CODES = Set.of(
            "LOW_SUGAR", "LOW_FAT", "LOW_TRANS_FAT", "LOW_SODIUM"
    );
    private static final BigDecimal LOW_SUGAR_LIMIT = new BigDecimal("5.0");
    private static final BigDecimal LOW_FAT_LIMIT = new BigDecimal("3.0");
    private static final BigDecimal LOW_SODIUM_LIMIT = new BigDecimal("0.12");

    @Override
    public boolean supports(RestrictionCategory category) {
        return category == RestrictionCategory.DIET;
    }

    @Override
    public void check(RestrictionRule rule, ProductData product, List<Finding> hits) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(hits, "hits");

        if (!supports(rule.category()) || !SUPPORTED_CODES.contains(rule.code())) {
            return;
        }

        switch (rule.code()) {
            case "LOW_SUGAR" -> checkMaximum(
                    rule.code(), product.nutrition(), nutrition -> nutrition.sugarsPer100g(),
                    "Sugar", LOW_SUGAR_LIMIT, hits
            );
            case "LOW_FAT" -> checkMaximum(
                    rule.code(), product.nutrition(), nutrition -> nutrition.fatPer100g(),
                    "Total fat", LOW_FAT_LIMIT, hits
            );
            case "LOW_TRANS_FAT" -> checkTransFat(product.nutrition(), hits);
            case "LOW_SODIUM" -> checkSodium(product.nutrition(), hits);
            default -> {
            }
        }
    }

    private void checkMaximum(
            String code,
            Nutrition nutrition,
            Function<Nutrition, BigDecimal> valueExtractor,
            String nutrientName,
            BigDecimal limit,
            List<Finding> hits
    ) {
        BigDecimal value = nutrition == null ? null : valueExtractor.apply(nutrition);
        if (warnIfUncheckable(code, nutrientName, value, hits)) {
            return;
        }

        if (value != null && value.compareTo(limit) > 0) {
            hits.add(new Finding(
                    code,
                    Finding.SUBJECT_NUTRITION,
                    nutrientName + " is " + format(value)
                            + " g per 100 g, above the " + displayCode(code) + " limit of "
                            + format(limit) + " g per 100 g."
            ));
        }
    }

    private void checkTransFat(Nutrition nutrition, List<Finding> hits) {
        String code = "LOW_TRANS_FAT";
        BigDecimal value = nutrition == null ? null : nutrition.transFatPer100g();
        if (warnIfUncheckable(code, "Trans fat", value, hits)) {
            return;
        }

        if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
            hits.add(new Finding(
                    code,
                    Finding.SUBJECT_NUTRITION,
                    "Trans fat is " + format(value)
                            + " g per 100 g; the " + displayCode(code)
                            + " rule requires a confirmed value of 0.00 g per 100 g."
            ));
        }
    }

    private void checkSodium(Nutrition nutrition, List<Finding> hits) {
        String code = "LOW_SODIUM";
        BigDecimal value = nutrition == null ? null : nutrition.sodiumPer100g();
        if (warnIfUncheckable(code, "Sodium", value, hits)) {
            return;
        }

        if (value != null && value.compareTo(LOW_SODIUM_LIMIT) > 0) {
            // Reported in grams for consistency with the other nutrient messages (sugar, fat),
            // rather than converting to milligrams which reads awkwardly for large values.
            hits.add(new Finding(
                    code,
                    Finding.SUBJECT_NUTRITION,
                    "Sodium is " + format(value)
                            + " g per 100 g, above the " + displayCode(code) + " limit of "
                            + format(LOW_SODIUM_LIMIT) + " g per 100 g."
            ));
        }
    }

    /**
     * When the nutrition value a preference needs is missing or invalid we cannot confirm
     * compliance, so we surface a WARNING that tells the user to check the physical food label
     * rather than silently ignoring it. Returns {@code true} when a finding was added.
     */
    private boolean warnIfUncheckable(
            String code, String nutrientName, BigDecimal value, List<Finding> hits) {
        if (value == null) {
            hits.add(new Finding(
                    code,
                    Finding.SUBJECT_NUTRITION,
                    nutrientName + " data is missing for the " + displayCode(code)
                            + " preference - please check the product's physical label."
            ));
            return true;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            hits.add(new Finding(
                    code,
                    Finding.SUBJECT_NUTRITION,
                    nutrientName + " data looks invalid (" + format(value)
                            + " per 100 g) for the " + displayCode(code)
                            + " preference - please check the product's physical label."
            ));
            return true;
        }
        return false;
    }

    /** Formats a quantity to exactly two decimal places (e.g. {@code 10.5664 -> "10.57"}). */
    private String format(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        // BigDecimal can round a tiny negative to "-0.00"; normalise it to "0.00".
        return (rounded.signum() == 0 ? BigDecimal.ZERO.setScale(2) : rounded).toPlainString();
    }

    /** Human-readable restriction code with underscores replaced by spaces (e.g. LOW SODIUM). */
    private String displayCode(String code) {
        return code == null ? "" : code.replace('_', ' ');
    }
}
