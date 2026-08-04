package com.canmakan.backend.knowledgebase.mcp.contract;

/**
 * Contract for the "dietary-rule lookup" MCP tool.
 *
 * @author XieHuayuan & Amelia
 */
public record DietaryRuleResult(
        String code,             // e.g. "HALAL", "LOW_SUGAR"
        String category,         // ALLERGEN / RELIGIOUS / DIET
        String description       // human-readable rule description
) {}
