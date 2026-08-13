package com.canmakan.backend.product.recommendation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Tier C discovery: expand the candidate pool using inferred tags, labels,
 * and sibling categories. Used when Tier A returns fewer than five SAFE items.
 */
@Service
class MlSparseCatalogRecommender {

    private static final int MAX_CANDIDATES = 50;

    private final AlternativeProductQueryService queryService;
    private final ProductFeatureEncoder featureEncoder;

    MlSparseCatalogRecommender(
            AlternativeProductQueryService queryService,
            ProductFeatureEncoder featureEncoder) {
        this.queryService = queryService;
        this.featureEncoder = featureEncoder;
    }

    boolean isSparseSource(CatalogProduct source) {
        return featureEncoder.isSparseSource(source);
    }

    List<CatalogProduct> discoverCandidates(
            CatalogProduct source,
            SubstituteDiscoveryProfile substituteProfile) {
        return discoverCandidates(source, substituteProfile, Set.of());
    }

    List<CatalogProduct> discoverCandidates(
            CatalogProduct source,
            SubstituteDiscoveryProfile substituteProfile,
            Set<String> alreadyFoundBarcodes) {

        boolean flourSubstituteDiscovery = isFlourSubstituteDiscovery(substituteProfile);
        boolean breadSubstituteDiscovery = isBreadSubstituteDiscovery(substituteProfile);
        boolean breakfastCerealSubstituteDiscovery = isBreakfastCerealSubstituteDiscovery(substituteProfile);
        boolean peanutSubstituteDiscovery = isPeanutSubstituteDiscovery(substituteProfile);
        boolean narrowSubstituteDiscovery = flourSubstituteDiscovery
                || breadSubstituteDiscovery
                || breakfastCerealSubstituteDiscovery
                || peanutSubstituteDiscovery;

        List<String> tags = new ArrayList<>();
        if (substituteProfile != null && substituteProfile.includeTags() != null) {
            tags.addAll(substituteProfile.includeTags());
        }
        if (flourSubstituteDiscovery
                && substituteProfile.beverageTags() != null) {
            for (String flourTag : substituteProfile.beverageTags()) {
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
                substituteProfile != null ? substituteProfile.beverageTags() : List.of(),
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
            if (additional.size() >= MAX_CANDIDATES) {
                break;
            }
        }

        return new ArrayList<>(additional.values());
    }

    private static boolean isFlourSubstituteDiscovery(SubstituteDiscoveryProfile profile) {
        return profile != null
                && profile.includeTags() != null
                && profile.includeTags().contains("en:gluten-free-flour");
    }

    private static boolean isBreadSubstituteDiscovery(SubstituteDiscoveryProfile profile) {
        return profile != null
                && profile.includeTags() != null
                && profile.includeTags().contains("Gluten free bread");
    }

    private static boolean isBreakfastCerealSubstituteDiscovery(SubstituteDiscoveryProfile profile) {
        return profile != null
                && profile.includeTags() != null
                && profile.includeTags().contains("Gluten free Breakfast cereals");
    }

    private static boolean isPeanutSubstituteDiscovery(SubstituteDiscoveryProfile profile) {
        return profile != null
                && profile.includeTags() != null
                && profile.includeTags().contains("en:nut-butters");
    }
}
