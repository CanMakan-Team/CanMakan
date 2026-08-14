package com.canmakan.backend.product.verdict;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deterministic keyword fallback: maps an ingredient name to a root allergen when the catalog could
 * not resolve it. Verbose Open Food Facts names such as "Enriched High Protein Wheat Flour" do not
 * match the catalog, so without this they stay unresolved and escalate to the LLM, which then
 * mis-tags every ingredient (water, sugar, yeast) with the restriction. Catching the real allergen
 * here means such a product is decided at Tier 1 and never escalates.
 *
 * <p>Matching is whole-word only, so "buckwheat" does not match "wheat" and "coconut" does not match
 * a nut keyword. The list is deliberately conservative - only near-unambiguous allergen words.
 *
 * @author XieHuayuan
 */
final class AllergenKeywords {

    private AllergenKeywords() {
    }

    // Keyword -> root allergen code. Only words that almost always indicate the allergen are listed.
    private static final Map<String, String> KEYWORD_ROOTS = new LinkedHashMap<>();

    static {
        for (String grain : new String[] {
                "wheat", "barley", "rye", "spelt", "triticale", "semolina", "durum",
                "farro", "bulgur", "couscous", "malt", "oat", "oats", "gluten"}) {
            KEYWORD_ROOTS.put(grain, "GLUTEN");
        }
        KEYWORD_ROOTS.put("peanut", "PEANUT");
        KEYWORD_ROOTS.put("peanuts", "PEANUT");
        KEYWORD_ROOTS.put("groundnut", "PEANUT");
    }

    /**
     * @param ingredientName the raw ingredient label
     * @return the matched root allergen code, or {@code null} when no keyword is present
     */
    static String matchRoot(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return null;
        }
        String name = ingredientName.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : KEYWORD_ROOTS.entrySet()) {
            if (Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b").matcher(name).find()) {
                return entry.getValue();
            }
        }
        return null;
    }
}
