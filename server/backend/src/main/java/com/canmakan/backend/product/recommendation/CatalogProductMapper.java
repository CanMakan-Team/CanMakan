package com.canmakan.backend.product.recommendation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.IngredientLabelParser;
import com.canmakan.backend.product.verdict.ProductData;

@Service
public class CatalogProductMapper {

    public ProductData toProductData(CatalogProduct product) {
        List<Ingredient> ingredients = parseIngredients(product.getIngredientsText());
        return new ProductData(
            product.getBarcode(),
            ingredients,
            product.getIngredientsText(),
            mergeTags(product.getLabelsTags(), product.getAllergens()),
            splitTags(product.getTracesTags()),
            product.toNutrition(),
            !ingredients.isEmpty()
        );
    }

    private List<String> mergeTags(String labelsTags, String allergensTags) {
        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(splitTags(labelsTags));
        merged.addAll(splitTags(allergensTags));
        return new ArrayList<>(merged);
    }

    private List<Ingredient> parseIngredients(String ingredientsText) {
        if (ingredientsText == null || ingredientsText.isBlank()) {
            return List.of();
        }
        return IngredientLabelParser.split(ingredientsText).stream()
            .map(token -> new Ingredient(token, null, null, false))
            .toList();
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
            .map(String::trim)
            .filter(tag -> !tag.isEmpty())
            .distinct()
            .toList();
    }
}
