package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.AllergenRelationshipResult;
import org.springframework.stereotype.Service;

/**
 * MCP tool: walk the allergen parent/root hierarchy (e.g. Whey -> Milk -> DAIRY).
 * Backed by the knowledgebase (MW).
 *
 * @author Amelia Wong
 */
@Service
public class AllergenRelationshipTool {

    // TODO: inject the knowledgebase repository for allergen relationships.

    /**
     * @param allergen queried allergen or ingredient
     * @return its parent and root allergen
     */
    public AllergenRelationshipResult lookup(String allergen) {
        // TODO: query allergen relationship graph, map to AllergenRelationshipResult.
        throw new UnsupportedOperationException("TODO: implement");
    }
}
