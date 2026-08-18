package com.canmakan.backend.product.recommendation.catalog;

import com.canmakan.backend.product.recommendation.filter.CategoryTagParser;
import com.canmakan.backend.product.recommendation.filter.SubstituteDiscoveryProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlternativeProductQueryService {

    private static final int MAX_CANDIDATES = 50;

    private final CatalogProductRepository catalogProductRepository;

    public List<CatalogProduct> findSameCategoryCandidates(CatalogProduct sourceProduct) {
        return findCandidates(sourceProduct);
    }

    public List<CatalogProduct> findCandidates(CatalogProduct sourceProduct) {
        if (sourceProduct == null || !sourceProduct.isRecommendationEligible()) {
            return List.of();
        }
        return catalogProductRepository
            .findCandidatesByCategory(
                sourceProduct.getMainCategoryEn(),
                sourceProduct.getBarcode())
            .stream()
            .limit(MAX_CANDIDATES)
            .toList();
    }

    public List<CatalogProduct> findSubstituteTagCandidates(
            CatalogProduct sourceProduct,
            SubstituteDiscoveryProfile profile) {
        if (sourceProduct == null
                || sourceProduct.getBarcode() == null
                || profile == null
                || profile.includeTags().isEmpty()) {
            return List.of();
        }

        Map<String, CatalogProduct> merged = new LinkedHashMap<>();
        addCategoryTagCandidates(merged, sourceProduct, profile);
        return new ArrayList<>(merged.values());
    }

    /**
     * Union of category tags, label tags, and sibling {@code main_category_en} values.
     * Capped at {@value #MAX_CANDIDATES}. Does not invent products outside the catalog.
     */
    public List<CatalogProduct> findExpandedSubstituteCandidates(
            CatalogProduct sourceProduct,
            SubstituteDiscoveryProfile profile) {
        if (sourceProduct == null || sourceProduct.getBarcode() == null || profile == null) {
            return List.of();
        }

        Map<String, CatalogProduct> merged = new LinkedHashMap<>();
        addCategoryTagCandidates(merged, sourceProduct, profile);
        if (merged.size() >= MAX_CANDIDATES) {
            return new ArrayList<>(merged.values());
        }
        addLabelTagCandidates(merged, sourceProduct, profile);
        if (merged.size() >= MAX_CANDIDATES) {
            return new ArrayList<>(merged.values());
        }
        addSiblingCategoryCandidates(merged, sourceProduct, profile);
        return new ArrayList<>(merged.values());
    }

    public Optional<CatalogProduct> findByBarcode(String barcode) {
        return catalogProductRepository.findById(barcode);
    }

    private void addCategoryTagCandidates(
            Map<String, CatalogProduct> merged,
            CatalogProduct sourceProduct,
            SubstituteDiscoveryProfile profile) {
        if (profile.includeTags() == null || profile.includeTags().isEmpty()) {
            return;
        }
        for (String includeTag : profile.includeTags()) {
            for (CatalogProduct candidate : catalogProductRepository.findCandidatesByCategoryTag(
                    includeTag,
                    sourceProduct.getBarcode())) {
                if (matchesSubstituteConstraints(candidate, profile)) {
                    merged.putIfAbsent(candidate.getBarcode(), candidate);
                }
                if (merged.size() >= MAX_CANDIDATES) {
                    return;
                }
            }
        }
    }

    private void addLabelTagCandidates(
            Map<String, CatalogProduct> merged,
            CatalogProduct sourceProduct,
            SubstituteDiscoveryProfile profile) {
        if (profile.labelTags() == null || profile.labelTags().isEmpty()) {
            return;
        }
        for (String labelTag : profile.labelTags()) {
            for (CatalogProduct candidate : catalogProductRepository.findCandidatesByLabelTag(
                    labelTag,
                    sourceProduct.getBarcode())) {
                if (matchesLabelTags(candidate, profile.labelTags())
                        && matchesExclusions(candidate, profile)) {
                    merged.putIfAbsent(candidate.getBarcode(), candidate);
                }
                if (merged.size() >= MAX_CANDIDATES) {
                    return;
                }
            }
        }
    }

    private void addSiblingCategoryCandidates(
            Map<String, CatalogProduct> merged,
            CatalogProduct sourceProduct,
            SubstituteDiscoveryProfile profile) {
        if (profile.siblingCategories() == null || profile.siblingCategories().isEmpty()) {
            return;
        }
        for (CatalogProduct candidate : catalogProductRepository.findCandidatesByCategories(
                profile.siblingCategories(),
                sourceProduct.getBarcode())) {
            if (matchesExclusions(candidate, profile)) {
                merged.putIfAbsent(candidate.getBarcode(), candidate);
            }
            if (merged.size() >= MAX_CANDIDATES) {
                return;
            }
        }
    }

    public static boolean matchesSubstituteConstraints(CatalogProduct candidate, SubstituteDiscoveryProfile profile) {
        if (candidate == null || profile == null) {
            return false;
        }
        return matchesIncludeTags(candidate, profile.includeTags())
                && matchesExclusions(candidate, profile);
    }

    public static boolean matchesExclusions(CatalogProduct candidate, SubstituteDiscoveryProfile profile) {
        if (candidate == null || profile == null) {
            return false;
        }
        Set<String> categoryTags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        if (CategoryTagParser.containsAny(categoryTags, profile.excludeCategoryTags())) {
            return false;
        }
        return !hasExcludedTrace(candidate, profile.excludeTracesTags());
    }

    private static boolean matchesIncludeTags(CatalogProduct candidate, List<String> includeTags) {
        Set<String> tags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        return CategoryTagParser.containsAny(tags, includeTags);
    }

    private static boolean matchesLabelTags(CatalogProduct candidate, List<String> labelTags) {
        Set<String> tags = CategoryTagParser.parseTags(candidate.getLabelsTags());
        return CategoryTagParser.containsAny(tags, labelTags);
    }

    public static boolean hasExcludedTrace(CatalogProduct candidate, List<String> excludeTracesTags) {
        if (excludeTracesTags == null || excludeTracesTags.isEmpty()) {
            return false;
        }
        Set<String> traces = CategoryTagParser.parseTags(candidate.getTracesTags());
        if (CategoryTagParser.containsAny(traces, excludeTracesTags)) {
            return true;
        }
        return traces.stream().anyMatch(AlternativeProductQueryService::containsPeanutToken);
    }

    private static boolean containsPeanutToken(String tag) {
        return tag != null && tag.toLowerCase().contains("peanut");
    }
}
