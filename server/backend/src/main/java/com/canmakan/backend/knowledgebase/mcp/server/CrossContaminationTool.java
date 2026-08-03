package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.CrossContaminationResult;
import org.springframework.stereotype.Service;

/**
 * MCP tool: analyse label text for cross-contamination phrases such as
 * "may contain nuts" or "produced in a facility that also processes milk".
 * Backed by the knowledgebase (MW).
 *
 * @author Amelia Wong
 */
@Service
public class CrossContaminationTool {

    // TODO: inject phrase patterns / knowledgebase for cross-contamination.

    /**
     * @param labelText the raw label / ingredients text
     * @return whether a cross-contamination phrase was found and which allergens
     */
    public CrossContaminationResult analyse(String labelText) {
        // TODO: match phrases, extract allergens, map to CrossContaminationResult.
        throw new UnsupportedOperationException("TODO: implement");
    }
}
