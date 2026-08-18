package com.canmakan.backend.product.recommendation.ranking;

import com.canmakan.backend.product.recommendation.catalog.AlternativeProductQueryService;
import com.canmakan.backend.product.recommendation.catalog.CatalogProduct;
import com.canmakan.backend.product.recommendation.filter.SubstituteDiscoveryProfile;
import com.canmakan.backend.product.recommendation.filter.SubstituteDiscoveryProfiles;
import java.util.ArrayList;
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

        boolean flourSubstituteDiscovery = discoveryProfiles.isFlourSubstituteDiscovery(substituteProfile);
        boolean breadSubstituteDiscovery = discoveryProfiles.isBreadSubstituteDiscovery(substituteProfile);
        boolean breakfastCerealSubstituteDiscovery =
                discoveryProfiles.isBreakfastCerealSubstituteDiscovery(substituteProfile);
        boolean peanutSubstituteDiscovery = discoveryProfiles.isPeanutSpreadSubstituteDiscovery(substituteProfile);
        boolean milkSubstituteDiscovery = discoveryProfiles.isMilkSubstituteDiscovery(substituteProfile);
        boolean narrowSubstituteDiscovery = flourSubstituteDiscovery
                || breadSubstituteDiscovery
                || breakfastCerealSubstituteDiscovery
                || peanutSubstituteDiscovery
                || milkSubstituteDiscovery;

        List<String> tags = new ArrayList<>();
        if (substituteProfile != null && substituteProfile.includeTags() != null) {
            tags.addAll(substituteProfile.includeTags());
        }
        if (flourSubstituteDiscovery
                && substituteProfile != null
                && substituteProfile.secondaryIncludeTags() != null) {
            for (String flourTag : substituteProfile.secondaryIncludeTags()) {
                if (!tags.contains(flourTag)) {
                    tags.add(flourTag);
                }
            }
        }
        if (!flourSubstituteDiscovery
                && !breakfastCerealSubstituteDiscovery
                && !peanutSubstituteDiscovery) {
            for (String inferredTag : featureEncoder.inferSubstituteTags(source)) {
                if (!tags.contains(inferredTag)) {
                    tags.add(inferredTag);
                }
            }
        }

        List<String> labelTags = narrowSubstituteDiscovery
                ? List.of()
                : substituteProfile != null && substituteProfile.labelTags() != null
                        ? substituteProfile.labelTags()
                        : List.of();
        List<String> siblingCategories = narrowSubstituteDiscovery
                ? List.of()
                : substituteProfile != null && substituteProfile.siblingCategories() != null
                        ? substituteProfile.siblingCategories()
                        : List.of();

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

        List<CatalogProduct> slice = new ArrayList<>(additional.values());
        if (slice.size() > MAX_CANDIDATES) {
            return new ArrayList<>(slice.subList(0, MAX_CANDIDATES));
        }
        return slice;
    }
}
