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
    private static final BigDecimal MILLIGRAMS_PER_GRAM = new BigDecimal("1000");

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
                    rule.code(), product.nutrition(), Nutrition::sugarsPer100g,
                    "Sugar", LOW_SUGAR_LIMIT, "5.0", hits
            );
            case "LOW_FAT" -> checkMaximum(
                    rule.code(), product.nutrition(), Nutrition::fatPer100g,
                    "Total fat", LOW_FAT_LIMIT, "3.0", hits
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
            String displayedLimit,
            List<Finding> hits
    ) {
        BigDecimal value = nutrition == null ? null : valueExtractor.apply(nutrition);
        if (isUnusable(value)) {
            return;
        }

        if (value.compareTo(limit) > 0) {
            hits.add(new Finding(
                    code,
                    Finding.SUBJECT_NUTRITION,
                    nutrientName + " is " + format(value)
                            + " g per 100 g, above the " + code + " limit of "
                            + displayedLimit + " g per 100 g."
            ));
        }
    }

    private void checkTransFat(Nutrition nutrition, List<Finding> hits) {
        String code = "LOW_TRANS_FAT";
        BigDecimal value = nutrition == null ? null : nutrition.transFatPer100g();
        if (isUnusable(value)) {
            return;
        }

        if (value.compareTo(BigDecimal.ZERO) > 0) {
            hits.add(new Finding(
                    code,
                    Finding.SUBJECT_NUTRITION,
                    "Trans fat is " + format(value)
                            + " g per 100 g; the LOW_TRANS_FAT rule requires a confirmed value of 0 g per 100 g."
            ));
        }
    }

    private void checkSodium(Nutrition nutrition, List<Finding> hits) {
        String code = "LOW_SODIUM";
        BigDecimal value = nutrition == null ? null : nutrition.sodiumPer100g();
        if (isUnusable(value)) {
            return;
        }

        if (value.compareTo(LOW_SODIUM_LIMIT) > 0) {
            BigDecimal milligrams = value.multiply(MILLIGRAMS_PER_GRAM);
            hits.add(new Finding(
                    code,
                    Finding.SUBJECT_NUTRITION,
                    "Sodium is " + format(milligrams)
                            + " mg per 100 g, above the LOW_SODIUM limit of 120 mg per 100 g."
            ));
        }
    }

    /**
     * Soft nutrition preferences must not warn on missing or invalid data - only an actual
     * exceedance produces a finding. Returns {@code true} when the value cannot be checked.
     */
    private static boolean isUnusable(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) < 0;
    }

    private String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
