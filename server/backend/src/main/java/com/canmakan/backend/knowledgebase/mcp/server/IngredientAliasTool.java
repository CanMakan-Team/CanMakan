package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.IngredientAliasResult;
import org.springframework.stereotype.Service;

/**
 * MCP tool: resolve an ingredient name (including chemical aliases) to its
 * canonical form and root allergen. Backed by the knowledgebase (MW).
 *
 * @author Amelia Wong
 */
@Service
public class IngredientAliasTool {

    // TODO: inject the knowledgebase repository for ingredient aliases.

    /**
     * @param ingredientName raw ingredient name from a label
     * @return the canonical ingredient + root allergen
     */
    public IngredientAliasResult lookup(String ingredientName) {
        // TODO: query alias table, map to IngredientAliasResult.
        throw new UnsupportedOperationException("TODO: implement");
    }
}
