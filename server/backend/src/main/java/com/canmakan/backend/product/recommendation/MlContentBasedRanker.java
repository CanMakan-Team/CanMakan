package com.canmakan.backend.product.recommendation;

import com.canmakan.backend.product.verdict.RestrictionRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Tier C content-based ranking using sparse feature cosine similarity,
 * optional nutrition proximity, and profile-aware boosts.
 *
 * <p>Score formula (capped at {@value #MAX_SCORE}):
 * <pre>
 *   score = min({@value #MAX_SCORE},
 *         contentSimilarity × {@value #CONTENT_SIM_SCALE}
 *       + nutritionSimilarity   (when source and candidate both have sugars and sodium per 100g)
 *       + lowSugarBoost         ({@value #LOW_SUGAR_BOOST} when LOW_SUGAR rule and nutrition pair absent)
 *       + packSizeBoost         ({@value PackSizeParser#PACK_SIZE_WEIGHT} × volume similarity for milk substitutes)
 *       + priorSafeBoost        ({@value #PRIOR_SAFE_BOOST} when barcode in prior safe scans)
 *       )
 * </pre>
 *
 * <p>{@code contentSimilarity} is cosine similarity of {@link ProductFeatureEncoder} vectors.
 * {@code nutritionSimilarity} is {@value #NUTRITION_WEIGHT} times the mean of per-nutrient
 * {@code 1 - min(1, |Δ| / range)} for sugars and sodium (ranges {@value #MAX_SUGAR_RANGE_G}
 * g and {@value #MAX_SODIUM_RANGE_G} g per 100g).
 */
@Service
class MlContentBasedRanker {

    private static final double LOW_SUGAR_BOOST = 0.12;
    private static final double PRIOR_SAFE_BOOST = 0.10;
    private static final double NUTRITION_WEIGHT = 0.15;
    private static final double CONTENT_SIM_SCALE = 0.63;
    private static final double MAX_SUGAR_RANGE_G = 50.0;
    private static final double MAX_SODIUM_RANGE_G = 3.0;
    private static final double MAX_SCORE = 0.99;
    private static final double DOMAIN_BOOST = 0.03;
    private static final double NUT_BUTTER_EXTRA_BOOST = 0.02;
    private static final double COOKING_PENALTY = 0.10;

    private final ProductFeatureEncoder featureEncoder;

    MlContentBasedRanker(ProductFeatureEncoder featureEncoder) {
        this.featureEncoder = featureEncoder;
    }

    List<AlternativeProductRanker.RankedAlternative> rank(
            CatalogProduct source,
            List<CatalogProduct> safeCandidates,
            List<RestrictionRule> rules,
            Set<String> priorSafeBarcodes) {
        return rank(source, safeCandidates, rules, priorSafeBarcodes, null);
    }

    List<AlternativeProductRanker.RankedAlternative> rank(
            CatalogProduct source,
            List<CatalogProduct> safeCandidates,
            List<RestrictionRule> rules,
            Set<String> priorSafeBarcodes,
            SubstituteDiscoveryProfile substituteProfile) {

        Map<String, Double> sourceVector = featureEncoder.encodeQuery(source);
        boolean prefersLowSugar = prefersLowSugar(rules);
        boolean nutritionAvailable = hasNutritionPair(source);
        boolean milkSubstituteDiscovery = isMilkSubstituteDiscovery(substituteProfile);
        boolean packSizeAvailable = milkSubstituteDiscovery
                && PackSizeParser.resolveVolumeMl(source).isPresent();

        List<AlternativeProductRanker.RankedAlternative> ranked = new ArrayList<>();
        for (CatalogProduct candidate : safeCandidates) {
            double similarity = CosineSimilarity.between(sourceVector, featureEncoder.encode(candidate));
            double score = similarity * CONTENT_SIM_SCALE;

            boolean nutritionMatched = nutritionAvailable && hasNutritionPair(candidate);
            boolean unsweetenedMatched = prefersLowSugar && featureEncoder.isUnsweetened(candidate);
            boolean packSizeMatched = packSizeAvailable
                    && PackSizeParser.isStrongPackSizeMatch(source, candidate);

            if (nutritionMatched) {
                score += nutritionSimilarity(source, candidate);
            } else if (unsweetenedMatched) {
                score += LOW_SUGAR_BOOST;
            }
            if (packSizeAvailable && PackSizeParser.resolveVolumeMl(candidate).isPresent()) {
                score += PackSizeParser.weightedBoost(source, candidate);
            }
            score += domainAdjustment(candidate, substituteProfile);
            if (priorSafeBarcodes.contains(candidate.getBarcode())) {
                score += PRIOR_SAFE_BOOST;
            }

            score = Math.min(score, MAX_SCORE);
            ranked.add(new AlternativeProductRanker.RankedAlternative(
                    candidate,
                    BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP),
                    resolveMatchReason(
                            priorSafeBarcodes.contains(candidate.getBarcode()),
                            nutritionMatched && prefersLowSugar,
                            unsweetenedMatched,
                            packSizeMatched)));
        }

        ranked.sort(Comparator.comparing(AlternativeProductRanker.RankedAlternative::score).reversed());
        return ranked;
    }

    private static boolean prefersLowSugar(List<RestrictionRule> rules) {
        if (rules == null) {
            return false;
        }
        return rules.stream().anyMatch(rule -> "LOW_SUGAR".equals(rule.code()));
    }

    private static boolean hasNutritionPair(CatalogProduct product) {
        return resolveSugarsPer100g(product) != null && resolveSodiumPer100g(product) != null;
    }

    private static BigDecimal resolveSugarsPer100g(CatalogProduct product) {
        return product.toNutrition().sugarsPer100g();
    }

    private static BigDecimal resolveSodiumPer100g(CatalogProduct product) {
        return product.toNutrition().sodiumPer100g();
    }

    private static double nutritionSimilarity(CatalogProduct source, CatalogProduct candidate) {
        double sugarDelta = Math.abs(
                resolveSugarsPer100g(source).doubleValue() - resolveSugarsPer100g(candidate).doubleValue());
        double sodiumDelta = Math.abs(
                resolveSodiumPer100g(source).doubleValue() - resolveSodiumPer100g(candidate).doubleValue());
        double sugarSim = 1.0 - Math.min(1.0, sugarDelta / MAX_SUGAR_RANGE_G);
        double sodiumSim = 1.0 - Math.min(1.0, sodiumDelta / MAX_SODIUM_RANGE_G);
        return ((sugarSim + sodiumSim) / 2.0) * NUTRITION_WEIGHT;
    }

    private static String resolveMatchReason(
            boolean priorSafe,
            boolean nutritionMatched,
            boolean unsweetenedMatched,
            boolean packSizeMatched) {
        if (priorSafe) {
            return "ml_prior_safe_scan";
        }
        if (nutritionMatched) {
            return "ml_nutrition_match";
        }
        if (unsweetenedMatched) {
            return "ml_unsweetened_substitute";
        }
        if (packSizeMatched) {
            return "ml_pack_size_match";
        }
        return "ml_similarity";
    }

    private static boolean isMilkSubstituteDiscovery(SubstituteDiscoveryProfile profile) {
        return profile != null
                && profile.includeTags() != null
                && profile.includeTags().contains("en:milk-substitutes");
    }

    private static double domainAdjustment(
            CatalogProduct candidate,
            SubstituteDiscoveryProfile profile) {
        if (profile == null || candidate == null) {
            return 0.0;
        }
        Set<String> tags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        double adjustment = 0.0;
        if (CategoryTagParser.containsAny(tags, profile.secondaryIncludeTags())) {
            adjustment += DOMAIN_BOOST;
        }
        if (profile.includeTags() != null
                && CategoryTagParser.containsAny(Set.copyOf(profile.includeTags()), List.of("en:nut-butters"))
                && CategoryTagParser.containsAny(tags, List.of("en:nut-butters"))) {
            adjustment += NUT_BUTTER_EXTRA_BOOST;
        }
        if (CategoryTagParser.containsAny(tags, profile.deprioritizeTags())) {
            adjustment -= COOKING_PENALTY;
        }
        return adjustment;
    }
}
