package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.IngredientAliasResult;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tool: resolve an ingredient name (including chemical aliases) to its
 * canonical form and root allergen.
 *
 * @author Amelia Wong
 */
@Service
public class IngredientAliasTool {

    private final DietaryKnowledgeRepository repository;

    public IngredientAliasTool(DietaryKnowledgeRepository repository) {
        this.repository = repository;
    }

    @Tool(name = "ingredient_alias_lookup", description = "Resolve a raw ingredient name (including chemical aliases) to its canonical form and root allergen.")
    public IngredientAliasResult lookup(
            @ToolParam(description = "Raw ingredient name from a product label") String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return new IngredientAliasResult("", "", null, false);
        }

        return repository.findIngredientAlias(ingredientName)
                .map(entry -> new IngredientAliasResult(
                        ingredientName,
                        entry.ingredientName(),
                        entry.rootAllergen(),
                        entry.chemicalAlias()))
                .orElseGet(() -> new IngredientAliasResult(ingredientName, ingredientName, null, false));
    }
}
