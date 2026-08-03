package com.canmakan.backend.knowledgebase.mcp.contract;

/**
 * Contract for the "allergen relationship lookup" MCP tool (parent/root hierarchy).
 * Shared seam between the MCP client (HY) and the MCP server (MW).
 *
 * @author XieHuayuan &amp; Amelia Wong (shared contract)
 */
public record AllergenRelationshipResult(
        String allergen,         // queried allergen, e.g. "Whey"
        String parentAllergen,   // e.g. "Milk"
        String rootAllergen      // e.g. "DAIRY"
) {
}
