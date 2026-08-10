package com.canmakan.backend.knowledgebase.repository;

import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.repository.DietaryRestrictionRepository;
import com.canmakan.backend.knowledgebase.model.DietaryRule;
import com.canmakan.backend.knowledgebase.model.ENumber;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.mcp.contract.CrossContaminationResult;

import jakarta.annotation.PostConstruct;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-memory dietary knowledge store used by the MCP knowledge tools.
 *
 * Loads ingredient aliases and allergen parent/root hierarchies from
 * {@link IngredientEntityRepository}, resolves dietary rules via
 * {@link DietaryRestrictionRepository}, and analyses label text / Open Food Facts
 * {@code traces_tags} for cross-contamination signals. Seeded once at startup
 * via {@link #initialize()}.
 *
 * @author Amelia
 */
@Repository
@NoArgsConstructor(force = true)
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class DietaryKnowledgeRepository {

    private final IngredientEntityRepository ingredientEntityRepository;
    private final DietaryRestrictionRepository dietaryRestrictionRepository;
    private final Map<String, Ingredient> ingredientAliases = new LinkedHashMap<>();
    private final Map<String, Ingredient> allergenRelationships = new LinkedHashMap<>();
    private final List<String> crossContaminationKeywords = new ArrayList<>();

    // Seed the cross contamination patterns at startup
    @PostConstruct
    public void initialize() {
        seedCrossContaminationPatterns();
        loadIngredientKnowledge();
    }

    private static final Pattern E_NUMBER_TOKEN = Pattern.compile(
        "^E\\d+[A-Z]*(?:\\([^)]*\\))?", // pattern: E followed by digits, optional uppercase letters, optional parentheses
        Pattern.CASE_INSENSITIVE);

    private static final Set<String> ANIMAL_ROOT_ALLERGENS = Set.of(
        "EGG", "DAIRY", "MILK", "FISH", "SHELLFISH", "PEANUT", "NUTS", "SESAME");

    // Find the ingredient alias by normalized name (exact catalog name, synonym, or E-code).
    public Optional<Ingredient> findIngredientAlias(String ingredientName) {
        return Optional.ofNullable(ingredientAliases.get(normalize(ingredientName)));
    }

    // Find the E-number by normalized key
    public Optional<ENumber> findENumber(String eNumber) {
        if (ingredientEntityRepository == null || eNumber == null || eNumber.isBlank()) {
            return Optional.empty();
        }

        String query = eNumber.trim();
        String queryKey = normalizeENumberKey(query);

        return ingredientEntityRepository.findByIngredientNameContainingIgnoreCase(query).stream()
                .filter(entity -> Boolean.TRUE.equals(entity.getIsChemicalAlias()))
                .filter(entity -> eNumberKeysMatch(extractLeadingENumber(entity.getIngredientName()), queryKey))
                .min(Comparator.comparingInt((IngredientEntity entity) -> entity.getIngredientName().length())
                        .thenComparing(IngredientEntity::getIngredientName, String.CASE_INSENSITIVE_ORDER))
                .map(entity -> new ENumber(
                        extractLeadingENumber(entity.getIngredientName()).orElse(queryKey),
                        entity.getIngredientName(),
                        entity.getParentAllergen() == null ? "" : entity.getParentAllergen(),
                        entity.getRootAllergen() == null ? "" : entity.getRootAllergen(),
                        isAnimalDerived(entity)));
    }

    // Find the allergen relationship by normalized allergen
    public Optional<Ingredient> findAllergenRelationship(String allergen) {
        return Optional.ofNullable(allergenRelationships.get(normalize(allergen)));
    }

    // Find the dietary rule by normalized code
    public Optional<DietaryRule> findDietaryRule(String code) {
        if (dietaryRestrictionRepository == null || code == null || code.isBlank()) {
            return Optional.empty();
        }

        return dietaryRestrictionRepository.findByCodeIgnoreCase(code.trim())
            .map(this::toDietaryRule);
    }

    // Analyse cross contamination by label text
    public Optional<CrossContaminationResult> analyseCrossContamination(String labelText) {
        return analyseCrossContamination(labelText, null);
    }

    // Analyse cross contamination by label text and traces_tags
    /**
     * Analyses both free-text label content and structured traces_tags from Open Food Facts.
     *
     * Milk-family hits always include both {@code MILK} and {@code DAIRY} so callers that
     * key off either vocabulary still match.
     *
     * @param labelText   raw ingredients / label text
     * @param tracesTags  OFF traces_tags entries (e.g. ["en:milk", "en:nuts", "en:soy"]) – can be null/empty
     */
    public Optional<CrossContaminationResult> analyseCrossContamination(
        String labelText, List<String> tracesTags
    ) {

        List<String> foundAllergens = new ArrayList<>();
        String matchedPhrase = null;

        // 1. Structured traces_tags from OFF (most reliable but data is sparse)
        List<String> normalizedTags = normalizeTracesTags(tracesTags);
        if (!normalizedTags.isEmpty()) {
            Map<String, String> tagMapping = Map.ofEntries(
                Map.entry("en:milk", "MILK"),
                Map.entry("en:dairy", "DAIRY"),
                Map.entry("en:nuts", "NUTS"),
                Map.entry("en:tree-nuts", "NUTS"),
                Map.entry("en:peanuts", "PEANUT"),
                Map.entry("en:soybeans", "SOY"),
                Map.entry("en:soy", "SOY"),
                Map.entry("en:sesame-seeds", "SESAME"),
                Map.entry("en:sesame", "SESAME"),
                Map.entry("en:gluten", "GLUTEN"),
                Map.entry("en:wheat", "GLUTEN"),
                Map.entry("en:eggs", "EGG"),
                Map.entry("en:egg", "EGG"),
                Map.entry("en:fish", "FISH"),
                Map.entry("en:crustaceans", "SHELLFISH"),
                Map.entry("en:molluscs", "SHELLFISH"),
                Map.entry("en:shellfish", "SHELLFISH")
            );

            // Iterate over the normalized tags and add the allergens to the found allergens list
            for (String cleanTag : normalizedTags) {
                String allergen = tagMapping.get(cleanTag);
                if (allergen != null) {
                    addAllergenCodes(foundAllergens, allergen);
                }
            }

            // If found allergens are not empty, set the matched phrase to the normalized tags
            if (!foundAllergens.isEmpty()) {
                matchedPhrase = "traces_tags: " + String.join(",", normalizedTags);
            }
        }

        // 2. Free-text phrase detection (fallback / additional signal)
        if (labelText != null && !labelText.isBlank()) {
            String normalized = normalize(labelText);

            for (String keyword : crossContaminationKeywords) {
                if (normalized.contains(keyword)) {
                    if (matchedPhrase == null) {
                        matchedPhrase = extractMatchedPhrase(labelText, keyword);
                    }

                    // Longer tokens first so "peanut" wins before "nut"
                    Map<String, String> textKeywords = new LinkedHashMap<>();
                    textKeywords.put("peanut", "PEANUT");
                    textKeywords.put("shellfish", "SHELLFISH");
                    textKeywords.put("crustacean", "SHELLFISH");
                    textKeywords.put("sesame", "SESAME");
                    textKeywords.put("gluten", "GLUTEN");
                    textKeywords.put("wheat", "GLUTEN");
                    textKeywords.put("dairy", "DAIRY");
                    textKeywords.put("milk", "MILK");
                    textKeywords.put("soy", "SOY");
                    textKeywords.put("fish", "FISH");
                    textKeywords.put("egg", "EGG");
                    textKeywords.put("nut", "NUTS");

                    for (Map.Entry<String, String> entry : textKeywords.entrySet()) {
                        if (containsAllergenToken(normalized, entry.getKey())) {
                            addAllergenCodes(foundAllergens, entry.getValue());
                        }
                    }
                    break;
                }
            }
        }

        if (foundAllergens.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new CrossContaminationResult(true, foundAllergens, matchedPhrase != null ? matchedPhrase : ""));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Adds an allergen code and, for the milk family, both {@code MILK} and {@code DAIRY}.
     */
    private void addAllergenCodes(List<String> foundAllergens, String allergen) {
        if (allergen == null || allergen.isBlank()) {
            return;
        }
        if (!foundAllergens.contains(allergen)) {
            foundAllergens.add(allergen);
        }
        if ("MILK".equals(allergen) && !foundAllergens.contains("DAIRY")) {
            foundAllergens.add("DAIRY");
        }
        if ("DAIRY".equals(allergen) && !foundAllergens.contains("MILK")) {
            foundAllergens.add("MILK");
        }
    }

    private List<String> normalizeTracesTags(List<String> tracesTags) {
        if (tracesTags == null || tracesTags.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String tag : tracesTags) {
            if (tag == null || tag.isBlank() || "0".equals(tag.trim())) {
                continue;
            }
            normalized.add(tag.trim().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    /**
     * Whole-word match with optional trailing {@code s} (nut/nuts, egg/eggs) to avoid
     * false positives like nutrition, coconut, or eggplant.
     */
    private boolean containsAllergenToken(String normalizedText, String token) {
        return java.util.regex.Pattern
            .compile("\\b" + java.util.regex.Pattern.quote(token) + "s?\\b")
            .matcher(normalizedText)
            .find();
    }

    /**
     * Tries to return a short, readable phrase around the matched keyword.
     */
    private String extractMatchedPhrase(String originalText, String keyword) {
        String lower = originalText.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(keyword.toLowerCase(Locale.ROOT));
        if (idx < 0) {
            return originalText.length() > 80 ? originalText.substring(0, 80) + "..." : originalText;
        }

        int start = Math.max(0, idx - 25);
        int end = Math.min(originalText.length(), idx + keyword.length() + 45);
        return originalText.substring(start, end).trim();
    }

    private void loadIngredientKnowledge() {
        ingredientAliases.clear();
        allergenRelationships.clear();

        if (ingredientEntityRepository != null) {
            ingredientEntityRepository.findAll().forEach(entity -> {
                String canonical = entity.getIngredientName();
                String root = entity.getRootAllergen();
                boolean chemicalAlias = Boolean.TRUE.equals(entity.getIsChemicalAlias());

                registerAlias(canonical, canonical, root, chemicalAlias);

                // Chemical rows are also addressable by bare E-code (E471) for alias lookup.
                if (chemicalAlias) {
                    extractLeadingENumber(canonical).ifPresent(code ->
                            registerAlias(code, canonical, root, true));
                }

                // Only ingredients with a known parent allergen participate in the hierarchy walk.
                if (entity.getParentAllergen() != null && !entity.getParentAllergen().isBlank()) {
                    registerRelationship(
                            canonical,
                            entity.getParentAllergen(),
                            root,
                            chemicalAlias);
                }
            });

            registerCommonSynonyms();
        }
    }

    /**
     * Extra query keys that map onto seeded canonical ingredient names.
     * Only registered when the canonical row already exists in the alias map.
     */
    private void registerCommonSynonyms() {
        registerSynonym("caseinate", "Sodium Caseinate");
        registerSynonym("sodium caseinate", "Sodium Caseinate");
        registerSynonym("whey powder", "Whey Powder");
        registerSynonym("skim milk powder", "Skimmed Milk Powder");
        registerSynonym("skimmed milk powder", "Skimmed Milk Powder");
        registerSynonym("whole milk powder", "Whole Milk Powder");
        registerSynonym("milk solids", "Milk Solids");
        registerSynonym("milk solid", "Milk Solids");
        registerSynonym("milk soild", "Milk Solids"); // common OFF OCR/typo
        registerSynonym("msg", "E621 (Monosodium Glutamate)");
        registerSynonym("monosodium glutamate", "E621 (Monosodium Glutamate)");
        registerSynonym("contains monosodium glutamate", "E621 (Monosodium Glutamate)");
        registerSynonym("tartrazine", "E102 (Tartrazine)");
        registerSynonym("carrageenan", "E407 (Carrageenan)");
        registerSynonym("lysozyme", "E1105 (Lysozyme from eggs)");
        registerSynonym("oat flour", "Whole Grain Oat Flour");
        registerSynonym("wholegrain oat flour", "Whole Grain Oat Flour");
        registerSynonym("potato", "Potato Starch / Flakes");
        registerSynonym("potato starch", "Potato Starch / Flakes");
        registerSynonym("potato flakes", "Potato Starch / Flakes");
        registerSynonym("vegetable oil", "Palm Oil");
        registerSynonym("silicon dioxide", "E551 (Silicon Dioxide / Anticaking Agent)");
        registerSynonym("e551", "E551 (Silicon Dioxide / Anticaking Agent)");

        // Common OFF tokens that are recognised but not allergen roots.
        registerKnownLabel("maltodextrin");
        registerKnownLabel("dextrose");
        registerKnownLabel("flavouring");
        registerKnownLabel("flavoring");
        registerKnownLabel("spice");
        registerKnownLabel("spices");
        registerKnownLabel("vegetable");
        registerKnownLabel("vegetable powder");
        registerKnownLabel("sodium salt");
    }

    /** Registers a free-text label as a known non-allergen ingredient (no root). */
    private void registerKnownLabel(String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        String trimmed = query.trim();
        if (ingredientAliases.containsKey(normalize(trimmed))) {
            return;
        }
        registerAlias(trimmed, trimmed, null, false);
    }

    private void registerSynonym(String aliasQuery, String canonicalName) {
        Ingredient canonical = ingredientAliases.get(normalize(canonicalName));
        if (canonical == null) {
            return;
        }
        registerAlias(
                aliasQuery,
                canonical.ingredientName(),
                canonical.rootAllergen(),
                canonical.chemicalAlias());
    }

    private DietaryRule toDietaryRule(DietaryRestriction restriction) {
        return new DietaryRule(restriction.getCode(), restriction.getCategory(), restriction.getDescription());
    }

    private void seedCrossContaminationPatterns() {
        crossContaminationKeywords.clear();
        crossContaminationKeywords.addAll(List.of(
            "may contain",
            "may contain traces",
            "produced in a facility",
            "manufactured in a facility",
            "facility that also processes",
            "shared equipment",
            "processed on equipment",
            "made on shared lines"
        ));
    }

    private void registerAlias(String query, String canonicalName, String rootAllergen, boolean chemicalAlias) {
        ingredientAliases.put(normalize(query), new Ingredient(canonicalName, null, rootAllergen, chemicalAlias));
    }

    private void registerRelationship(String allergen, String parentAllergen, String rootAllergen, boolean chemicalAlias) {
        allergenRelationships.put(normalize(allergen), new Ingredient(allergen, parentAllergen, rootAllergen, chemicalAlias));
    }

    private boolean isAnimalDerived(IngredientEntity entity) {
        String root = entity.getRootAllergen() == null ? "" : entity.getRootAllergen().trim().toUpperCase(Locale.ROOT);
        if (ANIMAL_ROOT_ALLERGENS.contains(root)) {
            return true;
        }

        String haystack = ((entity.getIngredientName() == null ? "" : entity.getIngredientName()) + " "
                + (entity.getParentAllergen() == null ? "" : entity.getParentAllergen()))
                .toLowerCase(Locale.ROOT);

        return haystack.contains("egg")
                || haystack.contains("milk")
                || haystack.contains("dairy")
                || haystack.contains("fish")
                || haystack.contains("shellfish")
                || haystack.contains("animal");
    }

    private static Optional<String> extractLeadingENumber(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = E_NUMBER_TOKEN.matcher(ingredientName.trim());
        if (!matcher.find() || matcher.start() != 0) {
            return Optional.empty();
        }
        return Optional.of(normalizeENumberKey(matcher.group()));
    }

    private static String normalizeENumberKey(String value) {
        if (value == null) {
            return "";
        }
        // Keep letter/digit only so E500(ii), E-471, and E471 compare equally on the code stem.
        String compact = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        Matcher matcher = Pattern.compile("^E\\d+[A-Z]*").matcher(compact);
        return matcher.find() ? matcher.group() : compact;
    }

    private static boolean eNumberKeysMatch(Optional<String> candidateKey, String queryKey) {
        if (queryKey.isBlank() || candidateKey.isEmpty()) {
            return false;
        }
        String candidate = candidateKey.get();
        // Exact code match only (E471 != E473; E47 does not match E471).
        return candidate.equals(queryKey);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

}
