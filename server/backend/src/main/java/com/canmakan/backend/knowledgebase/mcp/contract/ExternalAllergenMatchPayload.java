package com.canmakan.backend.knowledgebase.mcp.contract;

import java.util.List;

/**
 * Structured mapping of unresolved ingredient names to CanMakan root codes,
 * produced from web-search grounding text (not from model memory alone).
 *
 * @author Amelia
 */
public record ExternalAllergenMatchPayload(List<Match> matches) {

    /**
     * One row: the label as supplied, plus a root code or {@code NONE}.
     */
    public record Match(String ingredient, String rootAllergen) {
    }
}
