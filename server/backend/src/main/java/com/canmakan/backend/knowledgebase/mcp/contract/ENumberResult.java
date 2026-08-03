package com.canmakan.backend.knowledgebase.mcp.contract;

/**
 * Contract for the "E-number lookup" MCP tool.
 * Shared seam between the MCP client (HY) and the MCP server (MW).
 *
 * @author XieHuayuan &amp; Amelia Wong (shared contract)
 */
public record ENumberResult(
        String eNumber,          // e.g. "E471"
        String name,             // e.g. "Mono- and diglycerides of fatty acids"
        String category,         // e.g. "emulsifier"
        boolean animalDerived    // true when it may be animal-derived (Halal/veg relevance)
) {
}
