package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.IngredientAliasResult;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tool: resolve a raw ingredient label to its canonical catalog name and root allergen.
 *
 * Supports exact catalog names (case/whitespace insensitive), common synonyms, and
 * chemical E-number codes (e.g. {@code E471}) that map to seeded chemical-alias rows.
 * Bare E-codes are also handled by {@code e_number_lookup} for richer additive metadata.
 *
 * Contract alignment with {@code DietaryKnowledgeMcpClient}: unresolved lookups keep
 * {@code rootAllergen == null} and passthrough {@code canonicalName} so the client can
 * fall through to allergen-relationship lookup.
 *
 * @author Amelia Wong
 */
@Service
@RequiredArgsConstructor
public class IngredientAliasTool {

    private final DietaryKnowledgeRepository repository;

    @Tool(
        name = "ingredient_alias_lookup",
        description = "Resolve a raw ingredient name to its canonical catalog form and root allergen. "
            + "Accepts exact names, common synonyms, and chemical E-number codes (e.g. E471). "
            + "Unresolved names return rootAllergen=null with canonicalName equal to the query."
    )
    public IngredientAliasResult lookup(
        @ToolParam(description = "Raw ingredient name from a product label") 
        String ingredientName
    ) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return new IngredientAliasResult("", "", null, false);
        }

        // Normalize the ingredient name by trimming
        String query = ingredientName.trim();

        // Find the ingredient alias by normalized name and map the result to IngredientAliasResult
        // If no result is found, return a default result with the normalized ingredient name and null root allergen
        return repository.findIngredientAlias(query)
                .map(entry -> new IngredientAliasResult(
                        query,
                        entry.ingredientName(),
                        blankToNull(entry.rootAllergen()),
                        entry.chemicalAlias()))
                .orElseGet(() -> new IngredientAliasResult(query, query, null, false));
    }

    // Helper method to convert a blank string to null
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
