package com.canmakan.backend.product.assessment.service;

import com.canmakan.backend.knowledgebase.mcp.server.AllergenRelationshipLookupFallback;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Last-resort fallback for the assessment flow: when a barcode lookup returns no usable ingredient
 * or allergen data, ask the external search provider which allergens the product (by name) contains
 * and turn the answer into confirmed-allergen ingredients for the verdict engine.
 *
 * <p>Only active when the external provider (Tavily) is configured; otherwise it returns nothing.
 * Web-derived allergens are best-effort evidence and must never be used to certify a product as
 * safe - the caller keeps the "verify the physical label" caution.
 *
 * @author XieHuayuan
 */
@Service
public class ProductNameAllergenLookup {

    private static final String ROOT_DAIRY = "DAIRY";
    private static final String ROOT_GLUTEN = "GLUTEN";
    private static final String ROOT_TREE_NUT = "TREE_NUT";
    private static final String ROOT_SHELLFISH = "SHELLFISH";

    // Keyword that may appear in the provider's answer -> CanMakan root allergen code.
    private static final Map<String, String> KEYWORD_ROOTS = new LinkedHashMap<>();

    static {
        KEYWORD_ROOTS.put("PEANUT", "PEANUT");
        KEYWORD_ROOTS.put(ROOT_DAIRY, ROOT_DAIRY);
        KEYWORD_ROOTS.put("MILK", ROOT_DAIRY);
        KEYWORD_ROOTS.put(ROOT_GLUTEN, ROOT_GLUTEN);
        KEYWORD_ROOTS.put("WHEAT", ROOT_GLUTEN);
        KEYWORD_ROOTS.put("TREE NUT", ROOT_TREE_NUT);
        KEYWORD_ROOTS.put(ROOT_TREE_NUT, ROOT_TREE_NUT);
        KEYWORD_ROOTS.put(ROOT_SHELLFISH, ROOT_SHELLFISH);
        KEYWORD_ROOTS.put("CRUSTACEAN", ROOT_SHELLFISH);
        KEYWORD_ROOTS.put("FISH", "FISH");
        KEYWORD_ROOTS.put("EGG", "EGG");
        KEYWORD_ROOTS.put("SESAME", "SESAME");
        KEYWORD_ROOTS.put("SOYA", "SOY");
        KEYWORD_ROOTS.put("SOY", "SOY");
    }

    private final AllergenRelationshipLookupFallback externalSearch;

    public ProductNameAllergenLookup(AllergenRelationshipLookupFallback externalSearch) {
        this.externalSearch = externalSearch;
    }

    /**
     * Derive confirmed-allergen ingredients from a product-name web search.
     *
     * @param productName the scanned product's name
     * @return allergen ingredients (root already set), or an empty list when the provider is
     *         unavailable or found nothing
     */
    public List<Ingredient> lookupByProductName(String productName) {
        String answer = externalSearch.searchProductAllergens(productName);
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        String upper = answer.toUpperCase(Locale.ROOT);

        // Deduplicate by root; the first matching keyword provides the display label.
        Map<String, String> rootToLabel = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : KEYWORD_ROOTS.entrySet()) {
            if (containsWord(upper, entry.getKey())) {
                rootToLabel.putIfAbsent(entry.getValue(), entry.getValue().toLowerCase(Locale.ROOT));
            }
        }

        List<Ingredient> allergens = new ArrayList<>();
        for (Map.Entry<String, String> entry : rootToLabel.entrySet()) {
            allergens.add(new Ingredient(
                entry.getValue() + " (from web search)", null, entry.getKey(), false));
        }
        return allergens;
    }

    private static boolean containsWord(String haystack, String keyword) {
        return Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b").matcher(haystack).find();
    }
}
