package com.canmakan.backend.knowledgebase.mcp.contract;

import com.canmakan.backend.knowledgebase.model.Ingredient;

import java.util.List;

/**
 * Structured contract for the "allergen relationship lookup" MCP tool.
 *
 * Local matches are fully resolved from the local database, while unresolved items
 * are left for the external fallback flow.
 * 
 * @author XieHuayuan & Amelia
 */
public record AllergenRelationshipResult(
        List<Ingredient> localMatches,
        List<String> unresolvedIngredients,
        String externalSearchSummary,
        List<Ingredient> externalMatches
) {}
