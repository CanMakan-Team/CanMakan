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
import com.canmakan.backend.product.verdict.IngredientResolution;
import com.canmakan.backend.product.verdict.IngredientResolver;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client side of the Dietary Knowledge MCP boundary (HY). Delegates the five lookups to
 * the {@code DietaryKnowledgeMcpServer} tools (MW) and implements {@link IngredientResolver}
 * so the verdict engine can resolve ingredients without knowing about the tools.
 *
 * <p>Marked {@link Primary} so it supersedes {@code IngredientResolverStub} as the
 * resolver the engine injects.
 *
 * <p>Resolution order in {@link #resolve}: alias &rarr; allergen hierarchy &rarr; E-number.
 * The E-number step lets a recognised additive embedded in a messy label token (e.g.
 * "Tricalcium Phosphate e341") be searched and classified as {@code KNOWN_NO_ALLERGEN}
 * instead of falsely degrading the verdict to WARNING. Allergen-linked or animal-derived
 * additives are never marked non-allergen, so we never emit a false SAFE.
 *
 * @author XieHuayuan
 */
@Primary
@Service
public class DietaryKnowledgeMcpClient implements IngredientResolver {

    /** Matches an E-number token such as "E341", "e 341", "E-471a". */
    private static final Pattern E_NUMBER = Pattern.compile("(?i)\\bE\\s*-?\\s*(\\d{3,4}[a-z]?)\\b");

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
     * Resolve an ingredient to a tri-state {@link IngredientResolution}.
     *
     * <ol>
     *   <li>alias lookup — resolves chemical aliases and direct root allergens;</li>
     *   <li>allergen hierarchy — e.g. Whey &rarr; Milk &rarr; DAIRY;</li>
     *   <li>E-number — extract the code (e.g. "E341") and classify the additive:
     *       allergen-linked &rarr; RESOLVED_ALLERGEN; recognised non-allergen,
     *       non-animal-derived additive &rarr; KNOWN_NO_ALLERGEN.</li>
     * </ol>
     * Anything else stays {@code UNKNOWN} so the engine emits WARNING.
     */
    @Override
    public IngredientResolution resolve(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return IngredientResolution.unknown();
        }

        // 1) Alias lookup on the full name.
        IngredientAliasResult alias = lookupAlias(ingredientName);
        if (hasRoot(alias)) {
            return IngredientResolution.allergen(alias.rootAllergen());
        }

        // 2) Allergen hierarchy on the canonical name.
        String canonical = (alias != null && alias.canonicalName() != null)
                ? alias.canonicalName()
                : ingredientName;
        String hierarchyRoot = firstRoot(lookupAllergenRelationship(canonical));
        if (hierarchyRoot != null) {
            return IngredientResolution.allergen(hierarchyRoot);
        }

        // 3) E-number embedded in the label token (e.g. "Tricalcium Phosphate e341").
        String eNumber = extractENumber(ingredientName);
        if (eNumber != null) {
            // 3a) Alias catalog knows this E-code -> trust its allergen linkage.
            IngredientAliasResult byCode = lookupAlias(eNumber);
            if (hasRoot(byCode)) {
                return IngredientResolution.allergen(byCode.rootAllergen());
            }
            if (byCode != null && byCode.chemicalAlias()) {
                return IngredientResolution.knownNoAllergen();   // known chemical, no allergen link
            }
            // 3b) Additive catalog: a recognised, non-animal-derived additive carries no allergen.
            ENumberResult additive = lookupENumber(eNumber);
            if (isKnownAdditive(additive) && !additive.animalDerived()) {
                return IngredientResolution.knownNoAllergen();
            }
        }

        return IngredientResolution.unknown();   // still unknown -> engine flags as WARNING
    }

    // --- knowledge-tool delegation (the MCP transport boundary) ------------------

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

    // --- helpers -----------------------------------------------------------------

    private static boolean hasRoot(IngredientAliasResult alias) {
        return alias != null && alias.rootAllergen() != null && !alias.rootAllergen().isBlank();
    }

    private static String firstRoot(AllergenRelationshipResult relationship) {
        if (relationship == null || relationship.localMatches() == null) {
            return null;
        }
        for (Ingredient match : relationship.localMatches()) {
            if (match != null && match.rootAllergen() != null && !match.rootAllergen().isBlank()) {
                return match.rootAllergen();
            }
        }
        return null;
    }

    private static boolean isKnownAdditive(ENumberResult additive) {
        return additive != null
                && additive.category() != null
                && !"unknown".equalsIgnoreCase(additive.category().trim());
    }

    /** Extract a normalised E-number (e.g. "E341") from a label token, or {@code null}. */
    private static String extractENumber(String ingredientName) {
        Matcher matcher = E_NUMBER.matcher(ingredientName);
        return matcher.find() ? "E" + matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }
}
