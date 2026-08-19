package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.ENumberResult;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tool: look up a food additive E-number and whether it may be animal-derived.
 *
 * @author Amelia Wong
 */
@Service
@RequiredArgsConstructor
public class ENumberTool {

    private final DietaryKnowledgeRepository repository;

    @Tool(
        name = "e_number_lookup", 
        description = "Look up a food additive E-number and whether it may be animal-derived."
    )
    public ENumberResult lookup(
        @ToolParam(description = "E-number to look up, e.g. E471") 
        String eNumber
    ) {
        if (eNumber == null || eNumber.isBlank()) {
            return new ENumberResult("", "", "", "", false);
        }

        // Normalize the E-number by trimming and converting to uppercase
        String normalized = normalizeENumberQuery(eNumber);

        // Find the E-number by normalized key and map the result to an ENumberResult
        // If no result is found, return a default result with the normalized E-number and UNKNOWN category
        return repository.findENumber(normalized)
            .map(entry -> new ENumberResult(
                entry.eNumber(),
                entry.name(),
                entry.category() == null ? "" : entry.category(),
                entry.rootAllergen() == null ? "" : entry.rootAllergen(),
                entry.animalDerived()))
            .orElseGet(() -> new ENumberResult(normalized, "Unknown additive", "unknown", "", false));
}

    // Helper method to normalize the E-number by trimming and converting to uppercase
    private static String normalizeENumberQuery(String eNumber) {
        String trimmed = eNumber.trim().toUpperCase();
        // Allow callers to pass "e-471" / "E 471" and still look up as E471
        return trimmed.replace(" ", "").replace("-", "");
    }
}
