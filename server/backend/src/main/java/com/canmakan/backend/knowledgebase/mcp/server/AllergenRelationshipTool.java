package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.AllergenRelationshipResult;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * MCP tool: walk the allergen parent/root hierarchy (e.g. Whey -> Milk -> DAIRY).
 *
 * The tool first checks each ingredient against the local database. Ingredients that
 * resolve locally are kept in the response, while unresolved ingredients are sent to
 * the external fallback flow.
 */
@AllArgsConstructor
@Service
public class AllergenRelationshipTool {

    private final DietaryKnowledgeRepository repository;
    private final AllergenRelationshipLookupFallback fallback;

    /**
     * MCP TOOL: Resolve a list of ingredient names against the local database and the fallback search.
     */
    @McpTool(
        name = "allergen_relationship_lookup",
        description = "Resolve ingredient > parent > root allergen hierarchy. Local DB first, then external fallback for unresolved items."
    )
    public AllergenRelationshipResult lookup(
        @McpToolParam (description = "List of ingredient names from the product label", required = true)
        List<String> ingredients
    ) {
        if (ingredients == null || ingredients.isEmpty()) {
            return new AllergenRelationshipResult(List.of(), List.of(), "", List.of());
        }

        List<Ingredient> localMatches = new ArrayList<>();
        List<String> unresolvedIngredients = new ArrayList<>();

        for (String ingredient : ingredients) {
            if (ingredient == null || ingredient.isBlank()) { continue; }

            // If ingredient exists in allergen rs db, add to local matches as Ingredient
            // Else, add to unresolved ingredients as String
            repository.findAllergenRelationship(ingredient)
                .ifPresentOrElse(localMatches::add, () -> unresolvedIngredients.add(ingredient.trim()));
        }

        // If no unresolved ingredients, send empty string as summary
        // Else, use fallback (last-resort web search) for unresolved ingredients.
        // Open Food Facts has already been consulted upstream during product lookup.
        String externalSummary = unresolvedIngredients.isEmpty()
            ? ""
            : fallback.searchExternal(unresolvedIngredients);

        return new AllergenRelationshipResult(localMatches, unresolvedIngredients, externalSummary, List.of());
    }

    /**
     * Backward-compatible overload that accepts a comma-separated ingredient string.
     * This is for legacy calling
     */
    public AllergenRelationshipResult lookup(String ingredientText) {
        if (ingredientText == null || ingredientText.isBlank()) {
            return new AllergenRelationshipResult(List.of(), List.of(), "", List.of());
        }

        List<String> ingredients = parseIngredients(ingredientText);
        return lookup(ingredients);
    }

    /**
     * Enrich a list of domain ingredients with roots/parents from the resolved hierarchy.
     * To be used in the assessment/orchestration layer before passing to verdict logic
     */
    public List<Ingredient> applyHierarchy(List<Ingredient> ingredients, AllergenRelationshipResult result) {
        if (ingredients == null || ingredients.isEmpty() || result == null) {
            return ingredients == null ? List.of() : ingredients;
        }

        List<Ingredient> enriched = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null) {
                continue;
            }

            Ingredient match = result.localMatches().stream()
                    .filter(entry -> entry != null && normalize(entry.ingredientName()).equals(normalize(ingredient.ingredientName())))
                    .findFirst()
                    .orElse(null);

            if (match != null) {
                enriched.add(new Ingredient(
                        ingredient.ingredientName(),
                        match.parentAllergen(),
                        match.rootAllergen(),
                        ingredient.chemicalAlias()));
            } else {
                enriched.add(ingredient);
            }
        }

        return enriched;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<String> parseIngredients(String ingredientText) {
        return Arrays.stream(ingredientText.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toList());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
