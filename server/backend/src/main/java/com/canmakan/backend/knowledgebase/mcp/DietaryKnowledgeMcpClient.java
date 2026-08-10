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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        return resolveWith(ingredientName, lookupAlias(ingredientName), null);
    }

    /**
     * Batched resolution: run the alias lookups, issue a <b>single</b> allergen-relationship
     * lookup for every label that still needs the hierarchy, then decide each label against
     * that shared result. Per-label outcomes match {@link #resolve(String)}.
     */
    @Override
    public Map<String, IngredientResolution> resolveAll(List<String> ingredientNames) {
        Map<String, IngredientResolution> resolutions = new LinkedHashMap<>();
        if (ingredientNames == null || ingredientNames.isEmpty()) {
            return resolutions;
        }

        // Phase 1: alias lookup per label; collect the hierarchy queries that still need it.
        Map<String, IngredientAliasResult> aliasByName = new LinkedHashMap<>();
        List<String> hierarchyQueries = new ArrayList<>();
        for (String name : ingredientNames) {
            if (name == null || name.isBlank() || aliasByName.containsKey(name)) {
                continue;
            }
            IngredientAliasResult alias = lookupAlias(name);
            aliasByName.put(name, alias);
            if (alias != null && alias.matched() && hasRoot(alias.rootAllergen())) {
                continue;   // fast path: no hierarchy lookup needed
            }
            hierarchyQueries.add(hierarchyQueryFor(alias, name));
        }

        // Phase 2: one shared allergen-relationship lookup for every label that needs it.
        AllergenRelationshipResult batchedHierarchy = hierarchyQueries.isEmpty()
                ? null
                : allergenRelationshipTool.lookup(hierarchyQueries);

        // Phase 3: resolve each label against its alias and the shared hierarchy result.
        for (String name : ingredientNames) {
            if (name == null || name.isBlank() || resolutions.containsKey(name)) {
                continue;
            }
            resolutions.put(name, resolveWith(name, aliasByName.get(name), batchedHierarchy));
        }
        return resolutions;
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

    /**
     * Shared decision logic for one label. {@code prefetchedHierarchy} lets the batched path
     * reuse a single allergen-relationship result; when {@code null} the hierarchy is looked
     * up on demand (the single-label path).
     */
    private IngredientResolution resolveWith(
            String ingredientName,
            IngredientAliasResult alias,
            AllergenRelationshipResult prefetchedHierarchy) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return IngredientResolution.unknown();
        }

        // Fast path: catalog already knows the root allergen.
        if (alias != null && alias.matched() && hasRoot(alias.rootAllergen())) {
            return IngredientResolution.resolved(alias.rootAllergen());
        }

        String hierarchyQuery = hierarchyQueryFor(alias, ingredientName);
        IngredientResolution fromHierarchy = resolveFromHierarchy(hierarchyQuery, prefetchedHierarchy);
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

    /** The label the hierarchy lookup should use: the alias canonical name when present. */
    private static String hierarchyQueryFor(IngredientAliasResult alias, String ingredientName) {
        return (alias != null && alias.canonicalName() != null && !alias.canonicalName().isBlank())
                ? alias.canonicalName()
                : ingredientName.trim();
    }

    /**
     * Resolve one label against the allergen hierarchy. When {@code prefetched} is supplied
     * (the batched path) matches must name-match strictly, so one label's hierarchy match
     * cannot leak onto a different label sharing the same batched result.
     */
    private IngredientResolution resolveFromHierarchy(
            String ingredientName, AllergenRelationshipResult prefetched) {
        boolean batched = prefetched != null;
        AllergenRelationshipResult relationship = batched
                ? prefetched
                : lookupAllergenRelationship(ingredientName);
        if (relationship == null) {
            return IngredientResolution.unknown();
        }

        IngredientResolution fromLocal = firstRootMatch(relationship.localMatches(), ingredientName, batched);
        if (fromLocal != null) {
            return fromLocal;
        }

        IngredientResolution fromExternal = firstRootMatch(relationship.externalMatches(), ingredientName, batched);
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
     * external source recognises the label as non-allergen (known-safe). When
     * {@code strictName} is set, a match must name-match the label (used by the batched
     * path where the result set covers several labels).
     */
    private static IngredientResolution firstRootMatch(
            List<Ingredient> matches, String ingredientName, boolean strictName) {
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
            if (!nameMatches && (strictName || matches.size() > 1)) {
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
