package com.canmakan.backend.knowledgebase.mcp.contract;

/**
 * Contract for the "ingredient alias lookup" MCP tool.
 *
 * @author XieHuayuan & Amelia
 */
public record IngredientAliasResult(
        String ingredientName,   // the queried name
        String canonicalName,    // resolved canonical ingredient
        String rootAllergen,     // e.g. "DAIRY", or null
        boolean chemicalAlias    // true when the query was a chemical alias
) {
}
