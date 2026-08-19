package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.DietaryRuleResult;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tool: fetch the definition/description of a dietary rule by code.
 *
 * @author Amelia Wong
 */
@Service
@RequiredArgsConstructor
public class DietaryRuleTool {

    private final DietaryKnowledgeRepository repository;

    @Tool(
        name = "dietary_rule_lookup", 
        description = "Fetch the definition and description of a dietary rule by its code (e.g. HALAL, LOW_SUGAR)."
    )
    public DietaryRuleResult lookup(
        @ToolParam(description = "Dietary rule code, e.g. HALAL or LOW_SUGAR") 
        String code
    ) {
        if (code == null || code.isBlank()) {
            return new DietaryRuleResult("", "", "");
        }

        // Normalize the code by trimming and converting to uppercase
        String normalizedCode = code.trim().toUpperCase();

        // Find the dietary rule by code and map the result to a DietaryRuleResult
        // If no result is found, return a default result with the normalized code and UNKNOWN category
        return repository.findDietaryRule(normalizedCode)
            .map(entry -> new DietaryRuleResult(
                entry.code(),
                entry.category(),
                entry.description() == null ? "" : entry.description()))
            .orElseGet(() -> new DietaryRuleResult(normalizedCode, "UNKNOWN", "No dietary rule definition found."));
    }
}
