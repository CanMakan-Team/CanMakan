package com.canmakan.backend.knowledgebase.mcp.contract;

/**
 * Contract for the "ingredient_alias_lookup" MCP tool.
 *
 * <p>{@code matched} is {@code true} when the query hit a catalog/synonym row (even if
 * {@code rootAllergen} is null, e.g. Salt). On a miss, {@code matched} is {@code false}
 * and {@code canonicalName} equals the trimmed query so the client can fall through to
 * allergen-relationship lookup.
 *
 * @author XieHuayuan & Amelia
 */
public record IngredientAliasResult(
        String ingredientName,   // the queried name (trimmed)
        String canonicalName,    // resolved canonical ingredient (or query on miss)
        String rootAllergen,     // e.g. "DAIRY", or null when none / miss
        boolean chemicalAlias,   // true when the query mapped to a chemical-alias catalog row
        boolean matched          // true when the query hit the catalog or a synonym
) {
}
