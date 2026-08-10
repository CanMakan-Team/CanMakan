package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UC5: AlternativeProductRanker")
class AlternativeProductRankerTest {

    private AlternativeProductRanker ranker;

    @BeforeEach
    void setUp() {
        ranker = new AlternativeProductRanker();
    }

    @Test
    void assignsPriorSafeScanReasonAndBoostCap() {
        CatalogProduct other = product("111", "Other cereal");
        CatalogProduct priorSafe = product("555", "Prior safe cereal");

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rank(
                List.of(other, priorSafe),
                Set.of("555")
        );

        AlternativeProductRanker.RankedAlternative priorRanked = ranked.stream()
                .filter(result -> "555".equals(result.product().getBarcode()))
                .findFirst()
                .orElseThrow();

        assertEquals("prior_safe_scan", priorRanked.matchReason());
        assertEquals(new BigDecimal("0.99"), priorRanked.score());
    }

    @Test
    void usesCategoryMatchWhenNoPriorSafeHistory() {
        CatalogProduct first = product("111", "First");
        CatalogProduct second = product("222", "Second");

        List<AlternativeProductRanker.RankedAlternative> ranked = ranker.rank(
                List.of(first, second),
                Set.of()
        );

        assertEquals("category_match", ranked.get(0).matchReason());
        assertEquals("111", ranked.get(0).product().getBarcode());
        assertEquals(new BigDecimal("1.0"), ranked.get(0).score());
        assertEquals(new BigDecimal("0.99"), ranked.get(1).score());
    }

    private static CatalogProduct product(String barcode, String name) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setProductName(name);
        product.setMainCategoryEn("Breakfast cereals");
        product.setIngredientsText("Rice flour");
        return product;
    }
}
