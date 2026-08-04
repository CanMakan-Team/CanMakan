package com.canmakan.backend.knowledgebase.mcp.contract;

/**
 * Contract for the "ingredient_alias_lookup" MCP tool.
 *
 * {@code rootAllergen} is {@code null} when unresolved (blank query or unknown name),
 * which matches {@code DietaryKnowledgeMcpClient.resolveRootAllergen} null/blank checks.
 * On a miss, {@code canonicalName} equals the trimmed query so the client can fall through
 * to allergen-relationship lookup.
 *
 * @author XieHuayuan & Amelia
 */
public record IngredientAliasResult(
        String ingredientName,   // the queried name (trimmed)
        String canonicalName,    // resolved canonical ingredient (or query on miss)
        String rootAllergen,     // e.g. "DAIRY", or null when unresolved
        boolean chemicalAlias    // true when the query mapped to a chemical-alias catalog row
) {
}
