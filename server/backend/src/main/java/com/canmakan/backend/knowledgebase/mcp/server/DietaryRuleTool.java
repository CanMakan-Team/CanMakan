package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.DietaryRuleResult;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;
import org.springframework.stereotype.Service;

/**
 * MCP tool: fetch the definition/description of a dietary rule by code.
 *
 * @author Amelia Wong
 */
@Service
public class DietaryRuleTool {

    private final DietaryKnowledgeRepository repository;

    public DietaryRuleTool(DietaryKnowledgeRepository repository) {
        this.repository = repository;
    }

    /**
     * @param code e.g. "HALAL", "LOW_SUGAR"
     * @return the rule's category and description
     */
    public DietaryRuleResult lookup(String code) {
        if (code == null || code.isBlank()) {
            return new DietaryRuleResult("", "", "");
        }

        return repository.findDietaryRule(code)
                .map(entry -> new DietaryRuleResult(entry.code(), entry.category(), entry.description()))
                .orElseGet(() -> new DietaryRuleResult(code, "UNKNOWN", "No dietary rule definition found."));
    }
}
