package com.canmakan.backend.product.verdict;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.model.Nutrition;

import java.util.List;

/**
 * Immutable snapshot of everything the verdict engine needs about a scanned product.
 * Assembled by an adapter from the {@code integration} lookup (Open Food Facts) and
 * {@code knowledgebase} ingredient resolution.
 *
 * @author XieHuayuan
 */
public record ProductData(
        String barcode,
        List<Ingredient> ingredients,   // knowledgebase.model.Ingredient
        String ingredientsText,         // raw label text (products.ingredients_text)
        List<String> labelTags,         // products.labels_tags, e.g. ["en:halal"]
        Nutrition nutrition,            // product.model.Nutrition
        boolean dataComplete            // false when ingredient data is missing/partial
) {
}
