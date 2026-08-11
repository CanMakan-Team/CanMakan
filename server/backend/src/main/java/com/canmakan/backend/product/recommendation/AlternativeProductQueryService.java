package com.canmakan.backend.product.recommendation;

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
        for (String includeTag : profile.includeTags()) {
            for (CatalogProduct candidate : catalogProductRepository.findCandidatesByCategoryTag(
                    includeTag,
                    sourceProduct.getBarcode())) {
                if (matchesIncludeTags(candidate, profile.includeTags())) {
                    merged.putIfAbsent(candidate.getBarcode(), candidate);
                }
                if (merged.size() >= MAX_CANDIDATES) {
                    return new ArrayList<>(merged.values());
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    public Optional<CatalogProduct> findByBarcode(String barcode) {
        return catalogProductRepository.findById(barcode);
    }

    private static boolean matchesIncludeTags(CatalogProduct candidate, List<String> includeTags) {
        Set<String> tags = CategoryTagParser.parseTags(candidate.getCategoryTags());
        return CategoryTagParser.containsAny(tags, includeTags);
    }
}
