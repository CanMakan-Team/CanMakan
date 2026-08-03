package com.canmakan.backend.knowledgebase.mcp.contract;

/**
 * Contract for the "dietary-rule lookup" MCP tool.
 * Shared seam between the MCP client (HY) and the MCP server (MW).
 *
 * @author XieHuayuan &amp; Amelia Wong (shared contract)
 */
public record DietaryRuleResult(
        String code,             // e.g. "HALAL", "LOW_SUGAR"
        String category,         // ALLERGEN / RELIGIOUS / DIET
        String description       // human-readable rule description
) {
}
