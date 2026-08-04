package com.canmakan.backend.knowledgebase.repository;

import com.canmakan.backend.dietaryprofile.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.DietaryRestriction;
import com.canmakan.backend.knowledgebase.model.DietaryRule;
import com.canmakan.backend.knowledgebase.model.ENumber;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.mcp.contract.CrossContaminationResult;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DietaryKnowledgeRepository {

    private final IngredientEntityRepository ingredientEntityRepository;
    private final DietaryProfileRepository dietaryProfileRepository;
    private final Map<String, Ingredient> ingredientAliases = new LinkedHashMap<>();
    private final Map<String, Ingredient> allergenRelationships = new LinkedHashMap<>();
    private final List<String> crossContaminationKeywords = new ArrayList<>();

    public DietaryKnowledgeRepository() {
        this(null, null);
    }

    public DietaryKnowledgeRepository(IngredientEntityRepository ingredientEntityRepository) {
        this(ingredientEntityRepository, null);
    }

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
     * @param labelText   raw ingredients / label text
     * @param tracesTags  OFF traces_tags string (e.g. "en:milk,en:nuts,en:soy") – can be null
     */
    public Optional<CrossContaminationResult> analyseCrossContamination(
        String labelText, String tracesTags
    ) {

        List<String> foundAllergens = new ArrayList<>();
        String matchedPhrase = null;

        // 1. Structured traces_tags from OFF (most reliable but data is sparse)

        if (tracesTags != null && !tracesTags.isBlank() && !"0".equals(tracesTags.trim())) {
            Map<String, String> tagMapping = Map.ofEntries(
                Map.entry("en:milk", "MILK"),
                Map.entry("en:dairy", "MILK"),
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

            for (String tag : tracesTags.split(",")) {
                String cleanTag = tag.trim().toLowerCase(Locale.ROOT);
                String allergen = tagMapping.get(cleanTag);
                if (allergen != null && !foundAllergens.contains(allergen)) {
                    foundAllergens.add(allergen);
                }
            }

            if (!foundAllergens.isEmpty()) {
                matchedPhrase = "traces_tags: " + tracesTags;
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

                    // Extract allergens mentioned near the phrase
                    Map<String, String> textKeywords = Map.ofEntries(
                        Map.entry("nut", "NUTS"),
                        Map.entry("peanut", "PEANUT"),
                        Map.entry("milk", "MILK"),
                        Map.entry("dairy", "MILK"),
                        Map.entry("soy", "SOY"),
                        Map.entry("sesame", "SESAME"),
                        Map.entry("gluten", "GLUTEN"),
                        Map.entry("wheat", "GLUTEN"),
                        Map.entry("egg", "EGG"),
                        Map.entry("fish", "FISH"),
                        Map.entry("shellfish", "SHELLFISH"),
                        Map.entry("crustacean", "SHELLFISH")
                    );

                    for (Map.Entry<String, String> entry : textKeywords.entrySet()) {
                        if (normalized.contains(entry.getKey()) && !foundAllergens.contains(entry.getValue())) {
                            foundAllergens.add(entry.getValue());
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
