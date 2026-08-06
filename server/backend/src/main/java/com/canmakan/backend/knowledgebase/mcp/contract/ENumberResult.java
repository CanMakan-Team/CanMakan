package com.canmakan.backend.knowledgebase.mcp.contract;

/**
 * Contract for the "E-number lookup" MCP tool.
 *
 * @author XieHuayuan & Amelia
 */
public record ENumberResult(
        String eNumber,          // e.g. "E471"
        String name,             // e.g. "Mono- and diglycerides of fatty acids"
        String category,         // e.g. "emulsifier"
        String rootAllergen,     // e.g. "EGG", "ADDITIVE"
        boolean animalDerived    // true when it may be animal-derived (Halal/veg relevance)
) {
}
