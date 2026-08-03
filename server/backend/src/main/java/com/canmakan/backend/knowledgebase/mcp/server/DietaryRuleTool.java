package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.DietaryRuleResult;
import org.springframework.stereotype.Service;

/**
 * MCP tool: fetch the definition/description of a dietary rule by code.
 * Backed by the knowledgebase (MW).
 *
 * @author Amelia Wong
 */
@Service
public class DietaryRuleTool {

    // TODO: inject the knowledgebase repository for dietary rules.

    /**
     * @param code e.g. "HALAL", "LOW_SUGAR"
     * @return the rule's category and description
     */
    public DietaryRuleResult lookup(String code) {
        // TODO: query dietary rule table, map to DietaryRuleResult.
        throw new UnsupportedOperationException("TODO: implement");
    }
}
