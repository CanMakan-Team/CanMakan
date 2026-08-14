package com.canmakan.backend.product.recommendation;

import com.canmakan.backend.product.model.Nutrition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Read model for Singapore catalog rows in {@code products}, used by UC5 Tier A
 * recommendation (query, rank, then rule-engine verification).
 *
 * <p>Maps the same table as {@link com.canmakan.backend.product.model.ScanProduct}
 * but includes the metadata needed for matching and {@code DietaryRuleEngine} checks.
 * Not every {@code products} column is mapped — add fields only when UC5 needs them.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products")
public class CatalogProduct {

    @Id
    @Column(name = "barcode", length = 50)
    private String barcode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "brand")
    private String brand;

    @Column(name = "main_category_en")
    private String mainCategoryEn;

    @Column(name = "category_tags", columnDefinition = "TEXT")
    private String categoryTags;

    @Column(name = "ingredients_text", columnDefinition = "TEXT")
    private String ingredientsText;

    @Column(name = "allergens", columnDefinition = "TEXT")
    private String allergens;

    @Column(name = "labels_tags", columnDefinition = "TEXT")
    private String labelsTags;

    @Column(name = "traces_tags", columnDefinition = "TEXT")
    private String tracesTags;

    @Column(name = "completeness", precision = 5, scale = 2)
    private BigDecimal completeness;

    @Column(name = "unique_scans_n")
    private Integer uniqueScansN;

    @Column(name = "sugars_100g", precision = 6, scale = 2)
    private BigDecimal sugars100g;

    @Column(name = "sodium_100g", precision = 6, scale = 2)
    private BigDecimal sodium100g;

    @Column(name = "fat_100g", precision = 6, scale = 2)
    private BigDecimal fat100g;

    @Column(name = "trans_fat_100g", precision = 6, scale = 2)
    private BigDecimal transFat100g;

    @Column(name = "saturated_fat_100g", precision = 6, scale = 2)
    private BigDecimal saturatedFat100g;

    @Column(name = "added_sugars_100g", precision = 6, scale = 2)
    private BigDecimal addedSugars100g;

    @Column(name = "salt_100g", precision = 6, scale = 2)
    private BigDecimal salt100g;

    @Column(name = "added_salt_100g", precision = 6, scale = 2)
    private BigDecimal addedSalt100g;

    /**
     *  Nutrition  fallbacks for sparse catalog rows.
     *
     * <p>Order matches {@link Nutrition}: sugars, sodium, trans fat, saturated fat,
     * total fat, energy (kcal).
     */
    public Nutrition toNutrition() {
        return new Nutrition(
            resolveSugarsPer100g(),
            resolveSodiumPer100g(),
            transFat100g,
            saturatedFat100g,
            resolveFatPer100g(),
            null
        );
    }

    /**
     * Minimum metadata to enter the recommendation pipeline. Ingredient text is optional
     * because many catalog rows are sparse; the rule engine checks ingredients only when present.
     */
    public boolean isRecommendationEligible() {
        return hasText(mainCategoryEn);
    }

    private BigDecimal resolveSugarsPer100g() {
        if (isPresent(sugars100g)) {
            return sugars100g;
        }
        if (isPresent(addedSugars100g)) {
            return addedSugars100g;
        }
        return null;
    }

    private BigDecimal resolveSodiumPer100g() {
        if (isPresent(sodium100g)) {
            return sodium100g;
        }
        if (isPresent(salt100g)) {
            return salt100g.multiply(new BigDecimal("0.4"));
        }
        if (isPresent(addedSalt100g)) {
            return addedSalt100g.multiply(new BigDecimal("0.4"));
        }
        return null;
    }

    private BigDecimal resolveFatPer100g() {
        if (isPresent(fat100g)) {
            return fat100g;
        }
        if (isPresent(saturatedFat100g)) {
            return saturatedFat100g;
        }
        return null;
    }

    private static boolean isPresent(BigDecimal value) {
        return value != null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
