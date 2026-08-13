package com.canmakan.backend.product.recommendation;

import java.util.Collection;
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

    /**
     * Tier A tag-based substitute candidates: delimiter-safe match on {@code category_tags}.
     */
    @Query("""
        select p from CatalogProduct p
        where p.barcode <> :excludeBarcode
          and p.ingredientsText is not null
          and trim(p.ingredientsText) <> ''
          and p.categoryTags is not null
          and concat(',', p.categoryTags, ',') like concat('%,', :categoryTag, ',%')
        order by coalesce(p.uniqueScansN, 0) desc, coalesce(p.completeness, 0) desc
        """)
    List<CatalogProduct> findCandidatesByCategoryTag(
            @Param("categoryTag") String categoryTag,
            @Param("excludeBarcode") String excludeBarcode
    );

    /**
     * Expanded discovery: delimiter-safe match on {@code labels_tags}.
     */
    @Query("""
        select p from CatalogProduct p
        where p.barcode <> :excludeBarcode
          and p.ingredientsText is not null
          and trim(p.ingredientsText) <> ''
          and p.labelsTags is not null
          and concat(',', p.labelsTags, ',') like concat('%,', :labelTag, ',%')
        order by coalesce(p.uniqueScansN, 0) desc, coalesce(p.completeness, 0) desc
        """)
    List<CatalogProduct> findCandidatesByLabelTag(
            @Param("labelTag") String labelTag,
            @Param("excludeBarcode") String excludeBarcode
    );

    /**
     * Sibling-category candidates for expanded substitute discovery.
     */
    @Query("""
        select p from CatalogProduct p
        where p.barcode <> :excludeBarcode
          and p.ingredientsText is not null
          and trim(p.ingredientsText) <> ''
          and p.mainCategoryEn in :categories
        order by coalesce(p.uniqueScansN, 0) desc, coalesce(p.completeness, 0) desc
        """)
    List<CatalogProduct> findCandidatesByCategories(
            @Param("categories") Collection<String> categories,
            @Param("excludeBarcode") String excludeBarcode
    );
}
