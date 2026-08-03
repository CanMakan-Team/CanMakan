package com.canmakan.backend.knowledgebase.mcp.contract;

import java.util.List;

/**
 * Contract for the "cross-contamination phrase analysis" MCP tool
 * (parses "may contain" / "produced in a facility with" statements).
 * Shared seam between the MCP client (HY) and the MCP server (MW).
 *
 * @author XieHuayuan &amp; Amelia Wong (shared contract)
 */
public record CrossContaminationResult(
        boolean mayContain,        // whether a trace/cross-contamination phrase was found
        List<String> allergens,    // allergens named in the phrase
        String phrase              // the matched source phrase
) {
}
