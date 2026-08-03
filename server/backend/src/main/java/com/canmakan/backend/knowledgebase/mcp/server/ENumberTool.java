package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.ENumberResult;
import org.springframework.stereotype.Service;

/**
 * MCP tool: look up a food additive E-number and whether it may be animal-derived.
 * Backed by the knowledgebase (MW).
 *
 * @author Amelia Wong
 */
@Service
public class ENumberTool {

    // TODO: inject the knowledgebase repository for additives / E-numbers.

    /**
     * @param eNumber e.g. "E471"
     * @return the additive name, category, and animal-derived flag
     */
    public ENumberResult lookup(String eNumber) {
        // TODO: query additives table, map to ENumberResult.
        throw new UnsupportedOperationException("TODO: implement");
    }
}
