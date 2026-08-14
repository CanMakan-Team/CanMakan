package com.canmakan.backend.knowledgebase.mcp.contract;

import com.canmakan.backend.knowledgebase.model.Ingredient;

import java.util.List;

/**
 * Structured contract for the "allergen relationship lookup" MCP tool.
 *
 * Local matches are fully resolved from the local database. Unresolved items are
 * listed in {@code unresolvedIngredients}, with optional prose from the external
 * fallback in {@code externalSearchSummary} (never {@code null}; use {@code ""} when
 * unavailable).
 *
 * {@code externalMatches} holds structured roots mapped from {@code externalSearchSummary}
 * (ChatClient JSON when AI is enabled, otherwise regex parse of the Tavily text).
 *
 * @author XieHuayuan & Amelia
 */
public record AllergenRelationshipResult(
        List<Ingredient> localMatches,
        List<String> unresolvedIngredients,
        String externalSearchSummary,
        List<Ingredient> externalMatches
) {}
