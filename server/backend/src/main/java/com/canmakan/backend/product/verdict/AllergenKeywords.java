package com.canmakan.backend.product.verdict;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * <p>Dairy needs an extra guard: "milk" and "yoghurt" also appear in plant-based substitutes such as
 * "almond milk" or "soy yoghurt", which are not dairy. Those words map to DAIRY only when the name
 * carries no plant-source qualifier. Unambiguous dairy words (whey, lactose, casein, ghee) have no
 * common plant version and always map to DAIRY.
 *
 * @author XieHuayuan
 */
final class AllergenKeywords {

    private AllergenKeywords() {
    }

    private static final String DAIRY = "DAIRY";

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

        // Unambiguous dairy words: whey, lactose, casein(ate) and ghee have no common plant-based
        // version, so they always indicate dairy and need no plant-qualifier guard.
        for (String dairy : new String[] {"whey", "lactose", "casein", "caseinate", "ghee"}) {
            KEYWORD_ROOTS.put(dairy, DAIRY);
        }
    }

    // Dairy words that also name plant-based substitutes; treated as dairy only when unqualified.
    private static final Set<String> QUALIFIABLE_DAIRY_WORDS = Set.of("milk", "yoghurt", "yogurt");

    // Plant sources that make a "milk"/"yoghurt" a non-dairy substitute (e.g. "almond milk").
    private static final Set<String> PLANT_MILK_QUALIFIERS = Set.of(
            "soy", "soya", "almond", "coconut", "rice", "cashew", "hazelnut",
            "macadamia", "hemp", "pea", "walnut", "flax", "quinoa", "plant");

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
            if (containsWord(name, entry.getKey())) {
                return entry.getValue();
            }
        }

        // "milk"/"yoghurt" indicate dairy only when the name has no plant-source qualifier, so
        // "almond milk" and "soy yoghurt" are correctly left unmatched (not dairy) here.
        boolean plantQualified = PLANT_MILK_QUALIFIERS.stream().anyMatch(q -> containsWord(name, q));
        if (!plantQualified) {
            for (String dairyWord : QUALIFIABLE_DAIRY_WORDS) {
                if (containsWord(name, dairyWord)) {
                    return DAIRY;
                }
            }
        }
        return null;
    }

    /** Whole-word, case-insensitive match, so "buttermilk" does not match the word "milk". */
    private static boolean containsWord(String name, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(name).find();
    }
}
