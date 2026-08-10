package com.canmakan.backend.product.recommendation;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlternativeProductQueryService {

    private static final int MAX_CANDIDATES = 50;

    private final CatalogProductRepository catalogProductRepository;

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

    public Optional<CatalogProduct> findByBarcode(String barcode) {
        return catalogProductRepository.findById(barcode);
    }
}