package com.canmakan.backend.knowledgebase.repository;

import com.canmakan.backend.dietaryprofile.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.DietaryRestriction;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory dietary knowledge store used by the MCP knowledge tools.
 *
 * Loads ingredient aliases and allergen parent/root hierarchies from
 * {@link IngredientEntityRepository}, resolves dietary rules via
 * {@link DietaryProfileRepository}, and analyses label text / Open Food Facts
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
    private final DietaryProfileRepository dietaryProfileRepository;
    private final Map<String, Ingredient> ingredientAliases = new LinkedHashMap<>();
    private final Map<String, Ingredient> allergenRelationships = new LinkedHashMap<>();
    private final List<String> crossContaminationKeywords = new ArrayList<>();

    @PostConstruct
    public void initialize() {
        seedCrossContaminationPatterns();
        loadIngredientKnowledge();
    }

    public Optional<Ingredient> findIngredientAlias(String ingredientName) {
        return Optional.ofNullable(ingredientAliases.get(normalize(ingredientName)));
    }

    public Optional<Ingredient> resolveIngredient(String ingredientName) {
        return findIngredientAlias(ingredientName);
    }

    public Optional<ENumber> findENumber(String eNumber) {
        if (ingredientEntityRepository == null || eNumber == null || eNumber.isBlank()) {
            return Optional.empty();
        }

        return ingredientEntityRepository.findByIngredientNameContainingIgnoreCase(eNumber.trim()).stream()
                .filter(entity -> Boolean.TRUE.equals(entity.getIsChemicalAlias()))
                .findFirst()
                .map(entity -> new ENumber(
                        eNumber.trim(),
                        entity.getIngredientName(),
                        entity.getParentAllergen(),
                        false));
    }

    public Optional<Ingredient> findAllergenRelationship(String allergen) {
        return Optional.ofNullable(allergenRelationships.get(normalize(allergen)));
    }

    public Optional<DietaryRule> findDietaryRule(String code) {
        if (dietaryProfileRepository == null || code == null || code.isBlank()) {
            return Optional.empty();
        }

        return dietaryProfileRepository.findRestrictionByCode(code.trim())
            .map(this::toDietaryRule);
    }

    // Old callers (tests + existing code) keep working
    public Optional<CrossContaminationResult> analyseCrossContamination(String labelText) {
        return analyseCrossContamination(labelText, null);
    }

    // New version that can also use traces_tags
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

            for (String cleanTag : normalizedTags) {
                String allergen = tagMapping.get(cleanTag);
                if (allergen != null) {
                    addAllergenCodes(foundAllergens, allergen);
                }
            }

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
                registerAlias(
                        entity.getIngredientName(),
                        entity.getIngredientName(),
                        entity.getRootAllergen(),
                        Boolean.TRUE.equals(entity.getIsChemicalAlias()));

                // Only ingredients with a known parent allergen participate in the hierarchy walk.
                if (entity.getParentAllergen() != null && !entity.getParentAllergen().isBlank()) {
                    registerRelationship(
                            entity.getIngredientName(),
                            entity.getParentAllergen(),
                            entity.getRootAllergen(),
                            Boolean.TRUE.equals(entity.getIsChemicalAlias()));
                }
            });
        }
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

}
