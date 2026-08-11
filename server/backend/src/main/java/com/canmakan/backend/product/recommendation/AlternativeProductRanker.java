package com.canmakan.backend.product.recommendation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class AlternativeProductRanker {

    public List<RankedAlternative> rank(
            List<CatalogProduct> safeCandidates,
            Set<String> priorSafeBarcodes) {

        List<RankedAlternative> ranked = new ArrayList<>();
        int position = 0;
        for (CatalogProduct candidate : safeCandidates) {
            double base = 1.0 - (position * 0.01); // preserve repo order
            boolean priorSafe = priorSafeBarcodes.contains(candidate.getBarcode());
            double score = priorSafe ? Math.min(base + 0.10, 0.99) : base;
            String reason = priorSafe ? "prior_safe_scan" : "category_match";
            ranked.add(new RankedAlternative(candidate, BigDecimal.valueOf(score), reason));
            position++;
        }
        ranked.sort(Comparator.comparing(RankedAlternative::score).reversed());
        return ranked;
    }

    public record RankedAlternative(
        CatalogProduct product,
        BigDecimal score,
        String matchReason
    ) {}
}