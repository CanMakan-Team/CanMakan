package com.canmakan.backend.knowledgebase.mcp;

import com.canmakan.backend.knowledgebase.mcp.contract.AllergenRelationshipResult;
import com.canmakan.backend.knowledgebase.mcp.contract.CrossContaminationResult;
import com.canmakan.backend.knowledgebase.mcp.contract.DietaryRuleResult;
import com.canmakan.backend.knowledgebase.mcp.contract.ENumberResult;
import com.canmakan.backend.knowledgebase.mcp.contract.IngredientAliasResult;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.mcp.server.AllergenRelationshipTool;
import com.canmakan.backend.knowledgebase.mcp.server.CrossContaminationTool;
import com.canmakan.backend.knowledgebase.mcp.server.DietaryRuleTool;
import com.canmakan.backend.knowledgebase.mcp.server.ENumberTool;
import com.canmakan.backend.knowledgebase.mcp.server.IngredientAliasTool;
import com.canmakan.backend.product.verdict.IngredientResolver;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Client side of the Dietary Knowledge MCP boundary (HY). Delegates the five lookups to
 * the {@code DietaryKnowledgeMcpServer} tools (MW) and implements {@link IngredientResolver}
 * so the verdict engine can resolve unknown ingredients without knowing about the tools.
 *
 * <p>Marked {@link Primary} so it supersedes {@code IngredientResolverStub} as the
 * resolver the engine injects.
 *
 * <p><b>Transport:</b> the tools run in-process in the same Spring Boot application, so
 * they are called directly here. When a real {@code spring-ai-mcp} transport is added,
 * only the five {@code lookup*}/{@code analyse*} methods need to be repointed at it — the
 * {@link #resolveRootAllergen} logic and the engine stay unchanged.
 *
 * @author XieHuayuan
 */
@Primary
@Service
public class DietaryKnowledgeMcpClient implements IngredientResolver {

    private final IngredientAliasTool ingredientAliasTool;
    private final ENumberTool eNumberTool;
    private final AllergenRelationshipTool allergenRelationshipTool;
    private final DietaryRuleTool dietaryRuleTool;
    private final CrossContaminationTool crossContaminationTool;

    public DietaryKnowledgeMcpClient(IngredientAliasTool ingredientAliasTool,
                                     ENumberTool eNumberTool,
                                     AllergenRelationshipTool allergenRelationshipTool,
                                     DietaryRuleTool dietaryRuleTool,
                                     CrossContaminationTool crossContaminationTool) {
        this.ingredientAliasTool = ingredientAliasTool;
        this.eNumberTool = eNumberTool;
        this.allergenRelationshipTool = allergenRelationshipTool;
        this.dietaryRuleTool = dietaryRuleTool;
        this.crossContaminationTool = crossContaminationTool;
    }

    /**
     * Resolve an ingredient (including chemical aliases) to its root allergen.
     * Tries the alias tool first (it returns the root directly and canonicalises the
     * name), then falls back to the allergen relationship hierarchy.
     *
     * @return the root allergen (e.g. "DAIRY"), or {@code null} if still unknown.
     */
    @Override
    public String resolveRootAllergen(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return null;
        }

        // 1) Alias lookup: resolves chemical aliases and may return a root allergen directly.
        IngredientAliasResult alias = lookupAlias(ingredientName);
        if (alias != null && alias.rootAllergen() != null && !alias.rootAllergen().isBlank()) {
            return alias.rootAllergen();
        }

        // 2) Fall back to the allergen hierarchy (e.g. Whey -> Milk -> DAIRY).
        String canonical = (alias != null && alias.canonicalName() != null)
                ? alias.canonicalName()
                : ingredientName;
        AllergenRelationshipResult relationship = lookupAllergenRelationship(canonical);
        if (relationship != null && relationship.localMatches() != null) {
            for (Ingredient match : relationship.localMatches()) {
                if (match != null && match.rootAllergen() != null && !match.rootAllergen().isBlank()) {
                    return match.rootAllergen();
                }
            }
        }

        return null;   // still unknown -> engine flags as unresolved (WARNING)
    }

    public IngredientAliasResult lookupAlias(String ingredientName) {
        return ingredientAliasTool.lookup(ingredientName);
    }

    public ENumberResult lookupENumber(String eNumber) {
        return eNumberTool.lookup(eNumber);
    }

    public AllergenRelationshipResult lookupAllergenRelationship(String ingredient) {
        return allergenRelationshipTool.lookup(ingredient);
    }

    public DietaryRuleResult lookupDietaryRule(String code) {
        return dietaryRuleTool.lookup(code);
    }

    public CrossContaminationResult analyseCrossContamination(String labelText) {
        return crossContaminationTool.analyse(labelText);
    }
}
