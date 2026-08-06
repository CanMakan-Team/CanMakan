package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.AllergenRelationshipResult;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;
import lombok.AllArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP tool: walk the allergen parent/root hierarchy (e.g. Whey -> Milk -> DAIRY).
 *
 * The tool first checks each ingredient against the local database. Ingredients that
 * resolve locally are kept in the response, while unresolved ingredients are sent to
 * the external fallback flow. Tavily prose is parsed into {@code externalMatches} when
 * a root allergen code can be extracted.
 */
@AllArgsConstructor
@Service
public class AllergenRelationshipTool {

    private final DietaryKnowledgeRepository repository;
    private final AllergenRelationshipLookupFallback fallback;

    /**
     * MCP TOOL: Resolve a list of ingredient names against the local database and the fallback search.
     */
    @Tool(name = "allergen_relationship_lookup", description = "Resolve ingredient > parent > root allergen hierarchy. Local DB first, then external fallback for unresolved items.")
    public AllergenRelationshipResult lookup(
        @ToolParam(description = "List of ingredient names from the product label") List<String> ingredients
    ) {
        if (ingredients == null || ingredients.isEmpty()) {
            return new AllergenRelationshipResult(List.of(), List.of(), "", List.of());
        }

        List<Ingredient> localMatches = new ArrayList<>();
        List<String> unresolvedIngredients = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (String ingredient : ingredients) {
            if (ingredient == null || ingredient.isBlank()) {
                continue;
            }

            String trimmed = ingredient.trim();
            String key = normalize(trimmed);
            if (!seen.add(key)) {
                continue;
            }

            // If the allergen relationship is found, add it to the local matches
            // If the allergen relationship is not found, add it to the unresolved ingredients
            repository.findAllergenRelationship(trimmed)
                .ifPresentOrElse(localMatches::add, () -> unresolvedIngredients.add(trimmed));
        }

        // Open Food Facts has already been consulted upstream during product lookup.
        String externalSummary = unresolvedIngredients.isEmpty()
            ? ""
            : Objects.requireNonNullElse(fallback.searchExternal(unresolvedIngredients), "");

        // Parse the external summary into ingredients
        List<Ingredient> externalMatches = ExternalAllergenMatchParser.parse(
            unresolvedIngredients, externalSummary);

        return new AllergenRelationshipResult(
            localMatches, unresolvedIngredients, externalSummary, externalMatches);
    }

    /**
     * Backward-compatible overload that accepts a comma-separated ingredient string.
     */
    public AllergenRelationshipResult lookup(String ingredientText) {
        if (ingredientText == null || ingredientText.isBlank()) {
            return new AllergenRelationshipResult(List.of(), List.of(), "", List.of());
        }

        return lookup(parseIngredients(ingredientText));
    }

    /**
     * Enrich a list of domain ingredients with roots/parents from the resolved hierarchy.
     * Intended for the assessment/orchestration layer before verdict logic.
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

            // Find the allergen relationship in the local matches or external matches
            Ingredient match = result.localMatches().stream()
                .filter(entry -> entry != null && normalize(entry.ingredientName()).equals(normalize(ingredient.ingredientName())))
                .findFirst()
                .orElseGet(() -> result.externalMatches() == null ? null : result.externalMatches().stream()
                        .filter(entry -> entry != null
                                && normalize(entry.ingredientName()).equals(normalize(ingredient.ingredientName())))
                        .findFirst()
                        .orElse(null));

            // If the allergen relationship is found, add it to the enriched list
            // If the allergen relationship is not found, add the ingredient to the enriched list
            if (match != null) {
                String root = match.rootAllergen();
                if (root != null && "NONE".equalsIgnoreCase(root)) {
                    root = null;
                }
                enriched.add(new Ingredient(
                        ingredient.ingredientName(),
                        match.parentAllergen(),
                        root,
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
