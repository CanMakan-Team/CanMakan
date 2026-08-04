package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.ENumberResult;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;
import org.springframework.stereotype.Service;

/**
 * MCP tool: look up a food additive E-number and whether it may be animal-derived.
 *
 * @author Amelia Wong
 */
@Service
public class ENumberTool {

    private final DietaryKnowledgeRepository repository;

    public ENumberTool(DietaryKnowledgeRepository repository) {
        this.repository = repository;
    }

    /**
     * @param eNumber e.g. "E471"
     * @return the additive name, category, and animal-derived flag
     */
    public ENumberResult lookup(String eNumber) {
        if (eNumber == null || eNumber.isBlank()) {
            return new ENumberResult("", "", "", false);
        }

        return repository.findENumber(eNumber)
            .map(entry -> new ENumberResult(entry.eNumber(), entry.name(), entry.category(), entry.animalDerived()))
            .orElseGet(() -> new ENumberResult(eNumber, "Unknown additive", "unknown", false));
    }
}
