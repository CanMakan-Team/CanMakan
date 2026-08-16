package com.canmakan.backend.product.verdict;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
 * "almond milk" or "soy yoghurt", which are not dairy. Those words map to DAIRY unless immediately
 * preceded by a plant-source qualifier, so "milk and soy sauce" or "milk chocolate with almonds"
 * still correctly map to DAIRY. Unambiguous dairy words (whey, lactose, casein, ghee) have no
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

    // Cached compiled patterns so containsWord/matchRoot never recompile a regex per call.
    private static final Map<String, Pattern> KEYWORD_ROOT_PATTERNS = KEYWORD_ROOTS.keySet().stream()
            .collect(Collectors.toMap(word -> word, AllergenKeywords::wordPattern, (a, b) -> a, LinkedHashMap::new));

    private static final Map<String, Pattern> QUALIFIABLE_DAIRY_PATTERNS = QUALIFIABLE_DAIRY_WORDS.stream()
            .collect(Collectors.toMap(word -> word, AllergenKeywords::wordPattern));

    // Matches a plant qualifier immediately preceding a dairy word (e.g. "almond milk", "soy yoghurt"),
    // so "milk" in "milk and soy sauce" or "milk chocolate with almonds" is still treated as dairy.
    private static final Map<String, Pattern> PLANT_QUALIFIED_DAIRY_PATTERNS = QUALIFIABLE_DAIRY_WORDS.stream()
            .collect(Collectors.toMap(word -> word, dairyWord -> Pattern.compile(
                    "\\b(" + PLANT_MILK_QUALIFIERS.stream().map(Pattern::quote)
                            .collect(Collectors.joining("|")) + ")\\s+" + Pattern.quote(dairyWord) + "\\b")));

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
            if (KEYWORD_ROOT_PATTERNS.get(entry.getKey()).matcher(name).find()) {
                return entry.getValue();
            }
        }

        // "milk"/"yoghurt" indicate dairy unless immediately preceded by a plant-source qualifier,
        // so "almond milk" and "soy yoghurt" are correctly left unmatched (not dairy) here, while
        // "milk and soy sauce" or "milk chocolate with almonds" still resolve to dairy.
        for (String dairyWord : QUALIFIABLE_DAIRY_WORDS) {
            if (QUALIFIABLE_DAIRY_PATTERNS.get(dairyWord).matcher(name).find()
                    && !PLANT_QUALIFIED_DAIRY_PATTERNS.get(dairyWord).matcher(name).find()) {
                return DAIRY;
            }
        }
        return null;
    }

    private static Pattern wordPattern(String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b");
    }
}
