package com.canmakan.backend.knowledgebase.mcp;

import com.canmakan.backend.knowledgebase.mcp.contract.AllergenRelationshipResult;
import com.canmakan.backend.knowledgebase.mcp.contract.CrossContaminationResult;
import com.canmakan.backend.knowledgebase.mcp.contract.DietaryRuleResult;
import com.canmakan.backend.knowledgebase.mcp.contract.ENumberResult;
import com.canmakan.backend.knowledgebase.mcp.contract.IngredientAliasResult;
import com.canmakan.backend.product.verdict.IngredientResolver;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Client side of the Dietary Knowledge MCP boundary (HY). Calls the five tools on
 * the {@code DietaryKnowledgeMcpServer} (MW) and implements {@link IngredientResolver}
 * so the verdict engine can resolve unknown ingredients without knowing about MCP.
 *
 * <p>Marked {@link Primary} so it supersedes {@code IngredientResolverStub} as the
 * resolver the engine injects.
 *
 * <p><b>Transport status:</b> the five {@code lookup*} methods are the MCP transport
 * boundary and will call the server's tools once the {@code spring-ai-mcp} client is
 * configured (Member 3's infra task). Until then they return {@code null}, which makes
 * {@link #resolveRootAllergen} degrade gracefully to "unresolved" (the engine then
 * emits WARNING rather than a false SAFE) — so the pipeline runs end-to-end today.
 *
 * @author XieHuayuan
 */
@Primary
@Service
public class DietaryKnowledgeMcpClient implements IngredientResolver {

    // TODO (transport, Member 3): inject the Spring AI MCP client / tool callbacks
    //   pointing at DietaryKnowledgeMcpServer, then replace the null returns below.

    /**
     * Resolve an ingredient (including chemical aliases) to its root allergen.
     * Tries the alias tool first (it returns the root directly and canonicalises the
     * name), then falls back to the allergen relationship graph.
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
        if (relationship != null
                && relationship.rootAllergen() != null
                && !relationship.rootAllergen().isBlank()) {
            return relationship.rootAllergen();
        }

        return null;   // still unknown -> engine flags as unresolved (WARNING)
    }

    public IngredientAliasResult lookupAlias(String ingredientName) {
        // TODO (transport): call the "ingredient_alias_lookup" MCP tool.
        return null;
    }

    public ENumberResult lookupENumber(String eNumber) {
        // TODO (transport): call the "e_number_lookup" MCP tool.
        return null;
    }

    public AllergenRelationshipResult lookupAllergenRelationship(String allergen) {
        // TODO (transport): call the "allergen_relationship_lookup" MCP tool.
        return null;
    }

    public DietaryRuleResult lookupDietaryRule(String code) {
        // TODO (transport): call the "dietary_rule_lookup" MCP tool.
        return null;
    }

    public CrossContaminationResult analyseCrossContamination(String labelText) {
        // TODO (transport): call the "cross_contamination_analysis" MCP tool.
        return null;
    }
}
