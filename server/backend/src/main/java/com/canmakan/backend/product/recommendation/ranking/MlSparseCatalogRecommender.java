package com.canmakan.backend.product.recommendation.ranking;

import com.canmakan.backend.product.recommendation.catalog.AlternativeProductQueryService;
import com.canmakan.backend.product.recommendation.catalog.CatalogProduct;
import com.canmakan.backend.product.recommendation.filter.SubstituteDiscoveryProfile;
import com.canmakan.backend.product.recommendation.filter.SubstituteDiscoveryProfiles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Tier C discovery: expand the candidate pool inside a curated use-type slice.
 * Recall order follows catalog tag-query popularity (SQL), not Java cosine ranking.
 */
@Service
public class MlSparseCatalogRecommender {

    private static final int MAX_CANDIDATES = 50;

    private final AlternativeProductQueryService queryService;
    private final ProductFeatureEncoder featureEncoder;
    private final SubstituteDiscoveryProfiles discoveryProfiles;

    public MlSparseCatalogRecommender(
            AlternativeProductQueryService queryService,
            ProductFeatureEncoder featureEncoder,
            SubstituteDiscoveryProfiles discoveryProfiles) {
        this.queryService = queryService;
        this.featureEncoder = featureEncoder;
        this.discoveryProfiles = discoveryProfiles;
    }

    public boolean isSparseSource(CatalogProduct source) {
        return featureEncoder.isSparseSource(source);
    }

    public List<CatalogProduct> discoverCandidates(
            CatalogProduct source,
            SubstituteDiscoveryProfile substituteProfile) {
        return discoverCandidates(source, substituteProfile, Set.of());
    }

    public List<CatalogProduct> discoverCandidates(
            CatalogProduct source,
            SubstituteDiscoveryProfile substituteProfile,
            Set<String> alreadyFoundBarcodes) {

        DiscoveryFlags flags = resolveDiscoveryFlags(substituteProfile);
        List<String> tags = buildTags(source, substituteProfile, flags);
        List<String> labelTags = resolveNarrowedList(
                flags.narrow(), substituteProfile == null ? null : substituteProfile.labelTags());
        List<String> siblingCategories = resolveNarrowedList(
                flags.narrow(), substituteProfile == null ? null : substituteProfile.siblingCategories());

        if (tags.isEmpty() && labelTags.isEmpty() && siblingCategories.isEmpty()) {
            return List.of();
        }

        SubstituteDiscoveryProfile discoveryProfile = new SubstituteDiscoveryProfile(
                tags,
                substituteProfile != null ? substituteProfile.secondaryIncludeTags() : List.of(),
                substituteProfile != null ? substituteProfile.deprioritizeTags() : List.of(),
                labelTags,
                siblingCategories,
                substituteProfile != null ? substituteProfile.excludeCategoryTags() : List.of(),
                substituteProfile != null ? substituteProfile.excludeTracesTags() : List.of());

        Map<String, CatalogProduct> additional =
                collectAdditionalCandidates(source, discoveryProfile, alreadyFoundBarcodes);
        return capCandidates(additional.values());
    }

    /** Which narrowed-discovery substitute profile applies, if any. */
    private record DiscoveryFlags(
            boolean narrow,
            boolean flour,
            boolean breakfastCereal,
            boolean peanut) {
    }

    private DiscoveryFlags resolveDiscoveryFlags(SubstituteDiscoveryProfile substituteProfile) {
        boolean flour = discoveryProfiles.isFlourSubstituteDiscovery(substituteProfile);
        boolean bread = discoveryProfiles.isBreadSubstituteDiscovery(substituteProfile);
        boolean breakfastCereal = discoveryProfiles.isBreakfastCerealSubstituteDiscovery(substituteProfile);
        boolean peanut = discoveryProfiles.isPeanutSpreadSubstituteDiscovery(substituteProfile);
        boolean milk = discoveryProfiles.isMilkSubstituteDiscovery(substituteProfile);
        boolean narrow = flour || bread || breakfastCereal || peanut || milk;
        return new DiscoveryFlags(narrow, flour, breakfastCereal, peanut);
    }

    private List<String> buildTags(
            CatalogProduct source, SubstituteDiscoveryProfile substituteProfile, DiscoveryFlags flags) {
        List<String> tags = new ArrayList<>();
        if (substituteProfile != null && substituteProfile.includeTags() != null) {
            tags.addAll(substituteProfile.includeTags());
        }
        if (flags.flour() && substituteProfile != null && substituteProfile.secondaryIncludeTags() != null) {
            addMissing(tags, substituteProfile.secondaryIncludeTags());
        }
        if (!flags.flour() && !flags.breakfastCereal() && !flags.peanut()) {
            addMissing(tags, featureEncoder.inferSubstituteTags(source));
        }
        return tags;
    }

    private static void addMissing(List<String> target, List<String> candidates) {
        for (String candidate : candidates) {
            if (!target.contains(candidate)) {
                target.add(candidate);
            }
        }
    }

    /** Narrowed discovery always drops this list; otherwise falls back to an empty list when unset. */
    private static List<String> resolveNarrowedList(boolean narrow, List<String> value) {
        if (narrow) {
            return List.of();
        }
        return value != null ? value : List.of();
    }

    private Map<String, CatalogProduct> collectAdditionalCandidates(
            CatalogProduct source,
            SubstituteDiscoveryProfile discoveryProfile,
            Set<String> alreadyFoundBarcodes) {
        Map<String, CatalogProduct> additional = new LinkedHashMap<>();
        Set<String> skip = alreadyFoundBarcodes == null ? Set.of() : alreadyFoundBarcodes;
        String sourceBarcode = source == null ? null : source.getBarcode();

        for (CatalogProduct candidate : queryService.findExpandedSubstituteCandidates(source, discoveryProfile)) {
            if (candidate.getBarcode() == null
                    || candidate.getBarcode().equals(sourceBarcode)
                    || skip.contains(candidate.getBarcode())) {
                continue;
            }
            additional.putIfAbsent(candidate.getBarcode(), candidate);
        }
        return additional;
    }

    private static List<CatalogProduct> capCandidates(Collection<CatalogProduct> candidates) {
        List<CatalogProduct> slice = new ArrayList<>(candidates);
        if (slice.size() > MAX_CANDIDATES) {
            return new ArrayList<>(slice.subList(0, MAX_CANDIDATES));
        }
        return slice;
    }
}
