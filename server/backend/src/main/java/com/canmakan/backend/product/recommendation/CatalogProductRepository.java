package com.canmakan.backend.product.recommendation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Queries {@link CatalogProduct} rows from the {@code products} catalog for UC5.
 */
public interface CatalogProductRepository extends JpaRepository<CatalogProduct, String> {

    /**
     * Tier A candidates: same English category, exclude scanned product, prefer popular rows.
     * Caller should still run {@code DietaryRuleEngine} — this query does not filter SAFE.
     */
    @Query("""
        select p from CatalogProduct p
        where p.mainCategoryEn = :mainCategoryEn
          and p.barcode <> :excludeBarcode
          and p.ingredientsText is not null
          and trim(p.ingredientsText) <> ''
        order by coalesce(p.uniqueScansN, 0) desc, coalesce(p.completeness, 0) desc
        """)
    List<CatalogProduct> findCandidatesByCategory(
            @Param("mainCategoryEn") String mainCategoryEn,
            @Param("excludeBarcode") String excludeBarcode
    );
}
