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

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client side of the Dietary Knowledge MCP boundary (HY). Delegates the five lookups to
 * the {@code DietaryKnowledgeMcpServer} tools (MW) and implements {@link IngredientResolver}
 * so the verdict engine can resolve unknown ingredients without knowing about the tools.
 *
 * <p>Marked {@link Primary} so it supersedes {@code IngredientResolverStub} as the
 * resolver the engine injects.
 *
 * @author XieHuayuan
 * @author Amelia
 */
@Primary
@RequiredArgsConstructor
@Service
public class DietaryKnowledgeMcpClient implements IngredientResolver {

    private final IngredientAliasTool ingredientAliasTool;
    private final ENumberTool eNumberTool;
    private final AllergenRelationshipTool allergenRelationshipTool;
    private final DietaryRuleTool dietaryRuleTool;
    private final CrossContaminationTool crossContaminationTool;

    /**
     * Resolve a raw ingredient label to {@link IngredientResolution.Kind#RESOLVED},
     * {@link IngredientResolution.Kind#KNOWN_SAFE}, or {@link IngredientResolution.Kind#UNKNOWN}.
     */
    @Override
    public IngredientResolution resolve(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return IngredientResolution.unknown();
        }

        IngredientAliasResult alias = lookupAlias(ingredientName);

        // Fast path: catalog already knows the root allergen.
        if (alias != null && alias.matched() && hasRoot(alias.rootAllergen())) {
            return IngredientResolution.resolved(alias.rootAllergen());
        }

        String hierarchyQuery = (alias != null && alias.canonicalName() != null && !alias.canonicalName().isBlank())
                ? alias.canonicalName()
                : ingredientName.trim();

        IngredientResolution fromHierarchy = resolveFromHierarchy(hierarchyQuery);
        if (fromHierarchy.kind() == IngredientResolution.Kind.RESOLVED
                || fromHierarchy.kind() == IngredientResolution.Kind.KNOWN_SAFE) {
            return fromHierarchy;
        }

        if (alias != null && alias.matched()) {
            // Catalog/synonym hit with no root allergen (Salt, Sugar, oils, …).
            return IngredientResolution.knownSafe();
        }

        IngredientResolution fromENumber = resolveFromENumber(ingredientName);
        if (fromENumber != null) {
            return fromENumber;
        }

        return IngredientResolution.unknown();
    }

    /**
     * @return the resolved root allergen, or {@code null} when known-safe or unknown
     */
    @Override
    public String resolveRootAllergen(String ingredientName) {
        IngredientResolution resolution = resolve(ingredientName);
        return resolution.kind() == IngredientResolution.Kind.RESOLVED
                ? resolution.rootAllergen()
                : null;
    }

    private IngredientResolution resolveFromHierarchy(String ingredientName) {
        AllergenRelationshipResult relationship = lookupAllergenRelationship(ingredientName);
        if (relationship == null) {
            return IngredientResolution.unknown();
        }

        IngredientResolution fromLocal = firstRootMatch(relationship.localMatches(), ingredientName);
        if (fromLocal != null) {
            return fromLocal;
        }

        IngredientResolution fromExternal = firstRootMatch(relationship.externalMatches(), ingredientName);
        if (fromExternal != null) {
            return fromExternal;
        }

        return IngredientResolution.unknown();
    }

    /**
     * Looks up a leading/embedded E-number when alias and hierarchy did not resolve the label.
     *
     * @return a resolution, or {@code null} when no usable E-number mapping exists
     */
    private IngredientResolution resolveFromENumber(String ingredientName) {
        String eNumber = extractENumber(ingredientName);
        if (eNumber == null) {
            return null;
        }

        ENumberResult result = lookupENumber(eNumber);
        if (result == null
                || result.eNumber() == null
                || result.eNumber().isBlank()
                || "Unknown additive".equalsIgnoreCase(result.name())
                || "unknown".equalsIgnoreCase(result.category())) {
            return null;
        }

        String root = result.rootAllergen() == null ? "" : result.rootAllergen().trim();
        if (root.isBlank() || "ADDITIVE".equalsIgnoreCase(root)) {
            root = result.animalDerived() ? "MEAT" : "ADDITIVE";
        } else {
            root = root.toUpperCase(Locale.ROOT);
        }

        String canonical = result.name() == null || result.name().isBlank()
            ? null
            : result.name().trim();
        return IngredientResolution.resolved(root, canonical, true);
    }

    // Pattern to match the E-number in the ingredient name
    private static final Pattern E_NUMBER_PATTERN =
        Pattern.compile("(?i)\\bE\\s*-?\\s*(\\d{3,4}[A-Z]*)\\b");

    // Extract the E-number from the ingredient name
    private static String extractENumber(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return null;
        }
        
        Matcher matcher = E_NUMBER_PATTERN.matcher(ingredientName.trim());
        if (!matcher.find()) {
            return null;
        }
        return "E" + matcher.group(1).toUpperCase(Locale.ROOT);
    }

    /**
     * Picks a structured match for {@code ingredientName}. {@code NONE} means the
     * external source recognises the label as non-allergen (known-safe).
     */
    private static IngredientResolution firstRootMatch(List<Ingredient> matches, String ingredientName) {
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        String wanted = normalize(ingredientName);
        for (Ingredient match : matches) {
            if (match == null || match.rootAllergen() == null || match.rootAllergen().isBlank()) {
                continue;
            }
            boolean nameMatches = match.ingredientName() == null
                || normalize(match.ingredientName()).equals(wanted)
                || normalize(match.ingredientName()).contains(wanted)
                || wanted.contains(normalize(match.ingredientName()));
            if (!nameMatches && matches.size() > 1) {
                continue;
            }
            String root = match.rootAllergen().trim().toUpperCase(Locale.ROOT);
            if ("NONE".equals(root)) {
                return IngredientResolution.knownSafe();
            }
            return IngredientResolution.resolved(root);
        }
        return null;
    }

    private static boolean hasRoot(String rootAllergen) {
        return rootAllergen != null && !rootAllergen.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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
