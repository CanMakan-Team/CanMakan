package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.ENumberResult;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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

    @Tool(name = "e_number_lookup", description = "Look up a food additive E-number and whether it may be animal-derived.")
    public ENumberResult lookup(
            @ToolParam(description = "E-number to look up, e.g. E471") String eNumber) {
        if (eNumber == null || eNumber.isBlank()) {
            return new ENumberResult("", "", "", false);
        }

        return repository.findENumber(eNumber)
            .map(entry -> new ENumberResult(entry.eNumber(), entry.name(), entry.category(), entry.animalDerived()))
            .orElseGet(() -> new ENumberResult(eNumber, "Unknown additive", "unknown", false));
    }
}
