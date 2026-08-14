package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns free-text external search answers (e.g. Tavily) into structured
 * {@link Ingredient} rows for {@code AllergenRelationshipResult.externalMatches}.
 *
 * <p>Preferred line shape (requested in the Tavily query):
 * {@code IngredientName -> ROOT_CODE} where {@code ROOT_CODE} is a known CanMakan
 * root (DAIRY, GLUTEN, …) or {@code NONE} when the ingredient is not an allergen.
 *
 * <p>Also accepts looser prose such as {@code Casein belongs to the dairy family}.
 *
 * @author Amelia
 */
final class ExternalAllergenMatchParser {

    // Pattern to match the arrow line
    private static final Pattern ARROW_LINE = Pattern.compile(
            "(?i)^\\s*[-*]?\\s*(.+?)\\s*(?:->|→|:|=)\\s*([A-Z_]+)\\s*$",
            Pattern.MULTILINE
    );

    // Used to map the root alias to the root code
    private static final Map<String, String> ROOT_ALIASES = Map.ofEntries(
        Map.entry("DAIRY", "DAIRY"),
        Map.entry("MILK", "DAIRY"),
        Map.entry("LACTOSE", "DAIRY"),
        Map.entry("GLUTEN", "GLUTEN"),
        Map.entry("WHEAT", "GLUTEN"),
        Map.entry("PEANUT", "PEANUT"),
        Map.entry("PEANUTS", "PEANUT"),
        Map.entry("TREE_NUT", "TREE_NUT"),
        Map.entry("TREE_NUTS", "TREE_NUT"),
        Map.entry("TREENUT", "TREE_NUT"),
        Map.entry("NUT", "TREE_NUT"),
        Map.entry("NUTS", "TREE_NUT"),
        Map.entry("FISH", "FISH"),
        Map.entry("SHELLFISH", "SHELLFISH"),
        Map.entry("CRUSTACEAN", "SHELLFISH"),
        Map.entry("EGG", "EGG"),
        Map.entry("EGGS", "EGG"),
        Map.entry("SOY", "SOY"),
        Map.entry("SOYA", "SOY"),
        Map.entry("SESAME", "SESAME"),
        Map.entry("MEAT", "MEAT"),
        Map.entry("ADDITIVE", "ADDITIVE"),
        Map.entry("NONE", "NONE"),
        Map.entry("UNKNOWN", "NONE"),
        Map.entry("N/A", "NONE")
    );

    // Private constructor to prevent instantiation
    private ExternalAllergenMatchParser() {}

    /** Canonical CanMakan root for a token such as {@code MILK} or {@code DAIRY}. */
    static String canonicalRoot(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        return ROOT_ALIASES.get(rawToken.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
    }

    /**
     * Parse a search summary into one {@link Ingredient} per unresolved label that
     * can be confidently mapped.
     */
    // 1. Find the arrow line in the summary
    // 2. Extract the ingredient name and the root code
    // 3. Use the ROOT_ALIASES map to map the root code to the root name
    // 4. If the root code is not in the ROOT_ALIASES map, skip it
    // 5. If the root code is in the ROOT_ALIASES map, add the ingredient to the list
    // 6. Return the list of ingredients
    static List<Ingredient> parse(List<String> unresolvedIngredients, String summary) {
        if (unresolvedIngredients == null || unresolvedIngredients.isEmpty()
                || summary == null || summary.isBlank()) {
            return List.of();
        }

        Map<String, Ingredient> byKey = new LinkedHashMap<>();

        Matcher arrowMatcher = ARROW_LINE.matcher(summary);
        while (arrowMatcher.find()) {
            String label = arrowMatcher.group(1).trim();
            String rootToken = arrowMatcher.group(2).trim().toUpperCase(Locale.ROOT);
            String root = ROOT_ALIASES.get(rootToken);
            if (root == null) {
                continue;
            }
            String matchedUnresolved = matchUnresolved(label, unresolvedIngredients);
            if (matchedUnresolved != null) {
                byKey.putIfAbsent(normalize(matchedUnresolved),
                        new Ingredient(matchedUnresolved, null, root, false));
            }
        }

        // Looser prose fallback: ingredient name appears near a known root word.
        String lowerSummary = summary.toLowerCase(Locale.ROOT);
        for (String unresolved : unresolvedIngredients) {
            if (unresolved == null || unresolved.isBlank()) {
                continue;
            }
            String key = normalize(unresolved);
            if (byKey.containsKey(key)) {
                continue;
            }
            if (!lowerSummary.contains(normalize(unresolved))) {
                continue;
            }
            String root = findRootNearIngredient(lowerSummary, unresolved);
            if (root != null) {
                byKey.put(key, new Ingredient(unresolved.trim(), null, root, false));
            }
        }

        return new ArrayList<>(byKey.values());
    }

    // Match the ingredient name in the summary to the ingredient name in the list
    private static String matchUnresolved(String labelFromSummary, List<String> unresolvedIngredients) {
        String labelKey = normalize(labelFromSummary);
        for (String unresolved : unresolvedIngredients) {
            if (unresolved == null || unresolved.isBlank()) {
                continue;
            }
            String unresolvedKey = normalize(unresolved);
            if (labelKey.equals(unresolvedKey)
                    || labelKey.contains(unresolvedKey)
                    || unresolvedKey.contains(labelKey)) {
                return unresolved.trim();
            }
        }
        return null;
    }

    // Find the root near the ingredient in the summary
    private static String findRootNearIngredient(String lowerSummary, String ingredient) {
        String ingredientKey = normalize(ingredient);
        int idx = lowerSummary.indexOf(ingredientKey);
        if (idx < 0) {
            return null;
        }
        int start = Math.max(0, idx - 40);
        int end = Math.min(lowerSummary.length(), idx + ingredientKey.length() + 60);
        String window = lowerSummary.substring(start, end);

        for (Map.Entry<String, String> entry : ROOT_ALIASES.entrySet()) {
            String alias = entry.getKey().toLowerCase(Locale.ROOT).replace('_', ' ');
            if (window.contains(alias) || window.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }
        return null;
    }

    // Remove whitespace and convert to lowercase
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
