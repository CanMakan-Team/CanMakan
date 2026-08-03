package com.canmakan.backend.knowledgebase.mcp.contract;

/**
 * Contract for the "ingredient alias lookup" MCP tool.
 * Shared seam between the MCP client (HY) and the MCP server (MW): agree on this
 * shape before implementing either side.
 *
 * @author XieHuayuan &amp; Amelia Wong (shared contract)
 */
public record IngredientAliasResult(
        String ingredientName,   // the queried name
        String canonicalName,    // resolved canonical ingredient
        String rootAllergen,     // e.g. "DAIRY", or null
        boolean chemicalAlias    // true when the query was a chemical alias
) {
}
