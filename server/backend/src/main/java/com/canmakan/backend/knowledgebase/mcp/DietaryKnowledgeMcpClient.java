package com.canmakan.backend.knowledgebase.mcp;

import com.canmakan.backend.knowledgebase.mcp.contract.AllergenRelationshipResult;
import com.canmakan.backend.knowledgebase.mcp.contract.CrossContaminationResult;
import com.canmakan.backend.knowledgebase.mcp.contract.DietaryRuleResult;
import com.canmakan.backend.knowledgebase.mcp.contract.ENumberResult;
import com.canmakan.backend.knowledgebase.mcp.contract.IngredientAliasResult;
import com.canmakan.backend.product.verdict.IngredientResolver;
import org.springframework.stereotype.Service;

/**
 * Client side of the Dietary Knowledge MCP boundary (HY). Calls the five tools on
 * the {@code DietaryKnowledgeMcpServer} via the Spring AI MCP client, and also
 * implements {@link IngredientResolver} so the verdict engine can resolve unknown
 * ingredients without knowing about MCP.
 *
 * @author XieHuayuan
 */
@Service
public class DietaryKnowledgeMcpClient implements IngredientResolver {

    // TODO: inject Spring AI MCP client / tool callbacks pointing at the MCP server.

    public IngredientAliasResult lookupAlias(String ingredientName) {
        // TODO: call "ingredient_alias_lookup" tool.
        throw new UnsupportedOperationException("TODO: implement");
    }

    public ENumberResult lookupENumber(String eNumber) {
        // TODO: call "e_number_lookup" tool.
        throw new UnsupportedOperationException("TODO: implement");
    }

    public AllergenRelationshipResult lookupAllergenRelationship(String allergen) {
        // TODO: call "allergen_relationship_lookup" tool.
        throw new UnsupportedOperationException("TODO: implement");
    }

    public DietaryRuleResult lookupDietaryRule(String code) {
        // TODO: call "dietary_rule_lookup" tool.
        throw new UnsupportedOperationException("TODO: implement");
    }

    public CrossContaminationResult analyseCrossContamination(String labelText) {
        // TODO: call "cross_contamination_analysis" tool.
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public String resolveRootAllergen(String ingredientName) {
        // TODO: use lookupAlias / lookupAllergenRelationship to resolve the root allergen.
        throw new UnsupportedOperationException("TODO: implement");
    }
}
