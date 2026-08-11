package com.canmakan.backend.product.recommendation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class AlternativeProductRanker {

    private static final double SUBSTITUTE_BASE_SCORE = 0.95;
    private static final double BEVERAGE_BOOST = 0.03;
    private static final double COOKING_PENALTY = 0.10;
    private static final double PRIOR_SAFE_BOOST = 0.10;
    private static final double MAX_SCORE = 0.99;

    public List<RankedAlternative> rankSameCategory(
            List<CatalogProduct> safeCandidates,
            Set<String> priorSafeBarcodes) {
        return rank(safeCandidates, priorSafeBarcodes, MatchProvenance.SAME_CATEGORY, null);
    }

    public List<RankedAlternative> rankSubstituteTags(
            List<CatalogProduct> safeCandidates,
            Set<String> priorSafeBarcodes,
            SubstituteDiscoveryProfile profile) {
        return rank(safeCandidates, priorSafeBarcodes, MatchProvenance.SUBSTITUTE_TAG, profile);
    }

    public List<RankedAlternative> rank(
            List<CatalogProduct> safeCandidates,
            Set<String> priorSafeBarcodes) {
        return rankSameCategory(safeCandidates, priorSafeBarcodes);
    }

    private List<RankedAlternative> rank(
            List<CatalogProduct> safeCandidates,
            Set<String> priorSafeBarcodes,
            MatchProvenance provenance,
            SubstituteDiscoveryProfile profile) {

        List<RankedAlternative> ranked = new ArrayList<>();
        int position = 0;
        for (CatalogProduct candidate : safeCandidates) {
            Set<String> tags = CategoryTagParser.parseTags(candidate.getCategoryTags());
            boolean priorSafe = priorSafeBarcodes.contains(candidate.getBarcode());
            boolean deprioritized = profile != null
                    && CategoryTagParser.containsAny(tags, profile.deprioritizeTags());

            double base = provenance == MatchProvenance.SUBSTITUTE_TAG
                    ? SUBSTITUTE_BASE_SCORE - (position * 0.01)
                    : 1.0 - (position * 0.01);

            if (provenance == MatchProvenance.SUBSTITUTE_TAG && profile != null) {
                if (CategoryTagParser.containsAny(tags, profile.beverageTags())) {
                    base += BEVERAGE_BOOST;
                }
                if (deprioritized) {
                    base -= COOKING_PENALTY;
                }
            }

            double score = priorSafe ? Math.min(base + PRIOR_SAFE_BOOST, MAX_SCORE) : base;
            String reason = resolveMatchReason(provenance, priorSafe, deprioritized);
            ranked.add(new RankedAlternative(candidate, BigDecimal.valueOf(score), reason));
            position++;
        }
        ranked.sort(Comparator.comparing(RankedAlternative::score).reversed());
        return ranked;
    }

    private static String resolveMatchReason(
            MatchProvenance provenance,
            boolean priorSafe,
            boolean deprioritized) {
        if (priorSafe) {
            return "prior_safe_scan";
        }
        if (provenance == MatchProvenance.SUBSTITUTE_TAG) {
            return deprioritized ? "substitute_category_cooking" : "substitute_category";
        }
        return "category_match";
    }

    public record RankedAlternative(
        CatalogProduct product,
        BigDecimal score,
        String matchReason
    ) {}
}
