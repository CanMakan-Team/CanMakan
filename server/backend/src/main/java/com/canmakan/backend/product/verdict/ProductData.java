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
 * @author Amelia Wong
 */
public record ProductData(
        String barcode,
        List<Ingredient> ingredients,   // knowledgebase.model.Ingredient
        String ingredientsText,         // raw label text (products.ingredients_text)
        List<String> labelTags,         // products.labels_tags, e.g. ["en:halal"]
        List<String> tracesTags,        // products.traces_tags, e.g. ["en:milk"]
        Nutrition nutrition,            // product.model.Nutrition
        boolean dataComplete            // false when ingredient data is missing/partial
) {
    public ProductData {
        tracesTags = tracesTags == null ? List.of() : List.copyOf(tracesTags);
    }

    /**
     * Backward-compatible constructor used by tests that omit traces tags.
     */
    public ProductData(
        String barcode,
        List<Ingredient> ingredients,
        String ingredientsText,
        List<String> labelTags,
        Nutrition nutrition,
        boolean dataComplete
    ) {
        this(barcode, ingredients, ingredientsText, labelTags, List.of(), nutrition, dataComplete);
    }
}
