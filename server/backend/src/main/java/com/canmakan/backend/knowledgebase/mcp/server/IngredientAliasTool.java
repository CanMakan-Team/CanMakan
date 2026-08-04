package com.canmakan.backend.knowledgebase.mcp.server;

import com.canmakan.backend.knowledgebase.mcp.contract.IngredientAliasResult;
import com.canmakan.backend.knowledgebase.repository.DietaryKnowledgeRepository;
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

    /**
     * @param ingredientName raw ingredient name from a label
     * @return the canonical ingredient + root allergen
     */
    public IngredientAliasResult lookup(String ingredientName) {
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
