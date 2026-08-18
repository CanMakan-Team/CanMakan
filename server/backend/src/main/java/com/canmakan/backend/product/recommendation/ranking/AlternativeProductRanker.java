package com.canmakan.backend.product.recommendation.ranking;

import com.canmakan.backend.product.recommendation.catalog.CatalogProduct;
import com.canmakan.backend.product.recommendation.discovery.MatchProvenance;
import com.canmakan.backend.product.recommendation.filter.CategoryTagParser;
import com.canmakan.backend.product.recommendation.filter.PackSizeParser;
import com.canmakan.backend.product.recommendation.filter.SubstituteDiscoveryProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class AlternativeProductRanker {

    private static final double SUBSTITUTE_BASE_SCORE = 0.95;
    private static final double SECONDARY_INCLUDE_BOOST = 0.03;
    private static final double NUT_BUTTER_EXTRA_BOOST = 0.02;
    private static final double COOKING_PENALTY = 0.10;
    private static final double PRIOR_SAFE_BOOST = 0.10;
    private static final double MAX_SCORE = 0.99;

    public List<RankedAlternative> rankSameCategory(
            List<CatalogProduct> safeCandidates,
            Set<String> priorSafeBarcodes) {
        return rank(null, safeCandidates, priorSafeBarcodes, MatchProvenance.SAME_CATEGORY, null);
    }

    public List<RankedAlternative> rankSubstituteTags(
            CatalogProduct source,
            List<CatalogProduct> safeCandidates,
            Set<String> priorSafeBarcodes,
            SubstituteDiscoveryProfile profile) {
        return rank(source, safeCandidates, priorSafeBarcodes, MatchProvenance.SUBSTITUTE_TAG, profile);
    }

    public List<RankedAlternative> rank(
            List<CatalogProduct> safeCandidates,
            Set<String> priorSafeBarcodes) {
        return rankSameCategory(safeCandidates, priorSafeBarcodes);
    }

    private List<RankedAlternative> rank(
            CatalogProduct source,
            List<CatalogProduct> safeCandidates,
            Set<String> priorSafeBarcodes,
            MatchProvenance provenance,
            SubstituteDiscoveryProfile profile) {

        boolean milkSubstituteDiscovery = isMilkSubstituteDiscovery(profile);
        boolean packSizeAvailable = milkSubstituteDiscovery
                && source != null
                && PackSizeParser.resolveVolumeMl(source).isPresent();

        List<RankedAlternative> ranked = new ArrayList<>();
        int position = 0;
        for (CatalogProduct candidate : safeCandidates) {
            Set<String> tags = CategoryTagParser.parseTags(candidate.getCategoryTags());
            boolean priorSafe = priorSafeBarcodes.contains(candidate.getBarcode());
            boolean deprioritized = profile != null
                    && CategoryTagParser.containsAnyIncludingMainCategory(
                            candidate.getCategoryTags(),
                            candidate.getMainCategoryEn(),
                            profile.deprioritizeTags());
            boolean packSizeMatched = packSizeAvailable
                    && PackSizeParser.isStrongPackSizeMatch(source, candidate);

            double base = provenance == MatchProvenance.SUBSTITUTE_TAG
                    ? SUBSTITUTE_BASE_SCORE - (position * 0.01)
                    : 1.0 - (position * 0.01);

            if (provenance == MatchProvenance.SUBSTITUTE_TAG && profile != null) {
                if (CategoryTagParser.containsAnyIncludingMainCategory(
                        candidate.getCategoryTags(),
                        candidate.getMainCategoryEn(),
                        profile.secondaryIncludeTags())) {
                    base += SECONDARY_INCLUDE_BOOST;
                }
                if (isPeanutSpreadSubstituteProfile(profile)
                        && CategoryTagParser.containsAny(tags, List.of("en:nut-butters"))) {
                    base += NUT_BUTTER_EXTRA_BOOST;
                }
                if (deprioritized) {
                    base -= COOKING_PENALTY;
                }
                if (packSizeAvailable && PackSizeParser.resolveVolumeMl(candidate).isPresent()) {
                    base += PackSizeParser.weightedBoost(source, candidate);
                }
            }

            double score = priorSafe ? Math.min(base + PRIOR_SAFE_BOOST, MAX_SCORE) : base;
            String reason = resolveMatchReason(provenance, priorSafe, deprioritized, packSizeMatched);
            ranked.add(new RankedAlternative(candidate, BigDecimal.valueOf(score), reason));
            position++;
        }
        ranked.sort(Comparator.comparing(RankedAlternative::score).reversed());
        return ranked;
    }

    private static String resolveMatchReason(
            MatchProvenance provenance,
            boolean priorSafe,
            boolean deprioritized,
            boolean packSizeMatched) {
        if (priorSafe) {
            return "prior_safe_scan";
        }
        if (provenance == MatchProvenance.SUBSTITUTE_TAG) {
            if (packSizeMatched) {
                return "substitute_pack_size";
            }
            return deprioritized ? "substitute_category_cooking" : "substitute_category";
        }
        return "category_match";
    }

    private static boolean isMilkSubstituteDiscovery(SubstituteDiscoveryProfile profile) {
        return profile != null
                && profile.includeTags() != null
                && profile.includeTags().contains("en:milk-substitutes");
    }

    private static boolean isPeanutSpreadSubstituteProfile(SubstituteDiscoveryProfile profile) {
        return profile.includeTags() != null
                && CategoryTagParser.containsAny(Set.copyOf(profile.includeTags()), List.of("en:nut-butters"));
    }

    public record RankedAlternative(
        CatalogProduct product,
        BigDecimal score,
        String matchReason
    ) {}
}
