package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.DietaryRuleResult;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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

    @Tool(name = "dietary_rule_lookup", description = "Fetch the definition and description of a dietary rule by its code (e.g. HALAL, LOW_SUGAR).")
    public DietaryRuleResult lookup(
            @ToolParam(description = "Dietary rule code, e.g. HALAL or LOW_SUGAR") String code) {
        if (code == null || code.isBlank()) {
            return new DietaryRuleResult("", "", "");
        }

        return repository.findDietaryRule(code)
                .map(entry -> new DietaryRuleResult(entry.code(), entry.category(), entry.description()))
                .orElseGet(() -> new DietaryRuleResult(code, "UNKNOWN", "No dietary rule definition found."));
    }
}
