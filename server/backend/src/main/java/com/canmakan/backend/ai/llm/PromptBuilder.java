package com.canmakan.backend.ai.llm;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.model.Nutrition;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Builds a deterministic, data-minimised evidence prompt for unresolved product
 * information. Final dietary verdicts remain the rule engine's responsibility.
 *
 * @author XieHuayuan
 * @author YangMaowei
 * @author Amelia Wong
 */
@Service
public class PromptBuilder {

    private static final String PROMPT_VERSION = "canmakan-evidence-v3";
    private static final String MISSING = "MISSING_OR_UNKNOWN";

    /**
     * Compose the prompt from the product snapshot and the profile's active rules.
     *
     * @return the compiled evidence prompt
     */
    public String build(ProductData product, List<RestrictionRule> rules) {
        validateInputs(product, rules);

        StringBuilder prompt = new StringBuilder();

        // Prompt version
        prompt.append("PROMPT_VERSION: ").append(PROMPT_VERSION).append('\n');

        // Task
        prompt.append("TASK: Return structured evidence only for unresolved or ambiguous ingredients.\n");

        // Final verdict prohibition
        prompt.append("FINAL_VERDICT_PROHIBITION: Do not decide or output SAFE, WARNING, or UNSAFE. ")
            .append("Do not output a verdict field.\n");

        // Tool use
        prompt.append("TOOL_USE:\n")
            .append("- You may call dietary knowledge tools before answering. Prefer tools over guessing.\n")
            .append("- For unresolved or ambiguous ingredients, call tools as needed:\n")
            .append("  - ingredient_alias_lookup — synonyms / catalog roots\n")
            .append("  - allergen_relationship_lookup — parent/root allergen hierarchy\n")
            .append("  - e_number_lookup — when an E-code appears (e.g. E471, E1105)\n")
            .append("  - dietary_rule_lookup — when unsure what an active restriction code means\n")
            .append("  - cross_contamination_analysis — on ingredientsText and/or traces tags ")
            .append("when may-contain / facility phrases or traces are present\n")
            .append("- After tool results, return only the evidence JSON schema below.\n");
        
        // Evidence rules
        prompt.append("EVIDENCE_RULES:\n")
            .append("- Do not fabricate evidence.\n")
            .append("- State uncertainty when evidence is insufficient or ambiguous.\n")
            .append("- Return rootAllergen as null when it cannot be determined.\n")
            .append("- Return confidence as a finite number from 0.0 to 1.0.\n")
            .append("- Do not present a low-confidence guess as established fact.\n")
            .append("- Do not infer missing nutrition as zero.\n")
            .append("- Do not treat an unmapped ingredient as safe.\n")
            .append("- analysisNotes is explanatory only and must not determine a verdict.\n")
            .append("- Limit analysis to ingredient normalization, aliases, E-numbers, root allergens, ")
            .append("cross-contamination signals, and the supplied restriction codes.\n");

        // Product
        prompt.append("PRODUCT:\n")
            .append("barcode=").append(quote(product.barcode())).append('\n')
            .append("ingredientsText=").append(nullableValue(product.ingredientsText())).append('\n')
            .append("labelTags=").append(quotedList(product.labelTags())).append('\n')
            .append("tracesTags=").append(quotedList(
                product.tracesTags() == null ? List.of() : product.tracesTags()
            )).append('\n');

        // Nutrition
        appendNutrition(prompt, product.nutrition());

        // Restrictions
        appendRestrictions(prompt, rules);

        // Unresolved ingredients
        appendUnresolvedIngredients(prompt, product.ingredients());

        // Output schema
        prompt.append("OUTPUT_SCHEMA:\n")
            .append("{\"resolvedIngredients\":[{\"ingredientName\":\"string\",")
            .append("\"rootAllergen\":\"string|null\",\"confidence\":0.0}],")
            .append("\"analysisNotes\":\"string\"}\n")
            .append("Return only this evidence object; deterministic orchestration creates findings.");

        return prompt.toString();
    }

    // --- Helper methods ---

    // Validate the inputs
    private void validateInputs(ProductData product, List<RestrictionRule> rules) {
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(rules, "rules must not be null");
        if (product.barcode() == null || product.barcode().isBlank()) {
            throw new IllegalArgumentException("product barcode must not be blank");
        }
        Objects.requireNonNull(product.ingredients(), "product ingredients must not be null");
        Objects.requireNonNull(product.labelTags(), "product labelTags must not be null");
        Objects.requireNonNull(product.tracesTags(), "product tracesTags must not be null");

        for (RestrictionRule rule : rules) {
            Objects.requireNonNull(rule, "rules must not contain null");
            if (rule.code() == null || rule.code().isBlank()
                || rule.category() == null || rule.severity() == null) {
                throw new IllegalArgumentException("restriction rule must be complete");
            }
        }
        for (Ingredient ingredient : product.ingredients()) {
            Objects.requireNonNull(ingredient, "product ingredients must not contain null");
        }
    }

    // Append the nutrition to the prompt
    private void appendNutrition(StringBuilder prompt, Nutrition nutrition) {
        prompt.append("NUTRITION_PER_100G:\n")
            .append("sugarsPer100g=").append(nutrientValue(
                nutrition == null ? null : nutrition.sugarsPer100g()
            )).append('\n')
            .append("sodiumPer100g=").append(nutrientValue(
                nutrition == null ? null : nutrition.sodiumPer100g()
            )).append('\n')
            .append("transFatPer100g=").append(nutrientValue(
                nutrition == null ? null : nutrition.transFatPer100g()
            )).append('\n')
            .append("saturatedFatPer100g=").append(nutrientValue(
                nutrition == null ? null : nutrition.saturatedFatPer100g()
            )).append('\n')
            .append("fatPer100g=").append(nutrientValue(
                nutrition == null ? null : nutrition.fatPer100g()
            )).append('\n')
            .append("energyKcalPer100g=").append(nutrientValue(
                nutrition == null ? null : nutrition.energyKcalPer100g()
            )).append('\n');
    }

    // Append the restrictions to the prompt
    private void appendRestrictions(StringBuilder prompt, List<RestrictionRule> rules) {
        prompt.append("APPLICABLE_RESTRICTIONS:\n");
        if (rules.isEmpty()) {
            prompt.append("[]\n");
            return;
        }
        for (RestrictionRule rule : rules) {
            prompt.append("- code=").append(quote(rule.code()))
                .append(", category=").append(rule.category().name())
                .append(", severity=").append(rule.severity().name())
                .append('\n');
        }
    }

    // Append the unresolved ingredients to the prompt
    private void appendUnresolvedIngredients(
        StringBuilder prompt,
        List<Ingredient> ingredients
    ) {
        List<Ingredient> unresolved = ingredients.stream()
            .filter(this::isUnresolvedOrAmbiguous)
            .toList();
        prompt.append("UNRESOLVED_OR_AMBIGUOUS_INGREDIENTS:\n");
        if (unresolved.isEmpty()) {
            prompt.append("[]\n");
            return;
        }
        for (Ingredient ingredient : unresolved) {
            prompt.append("- ingredientName=").append(quote(ingredient.ingredientName()))
                .append(", parentAllergen=").append(nullableValue(ingredient.parentAllergen()))
                .append(", rootAllergen=").append(nullableValue(ingredient.rootAllergen()))
                .append(", chemicalAlias=").append(ingredient.chemicalAlias())
                .append('\n');
        }
    }

    // Check if the ingredient is unresolved or ambiguous
    private boolean isUnresolvedOrAmbiguous(Ingredient ingredient) {
        return ingredient.parentAllergen() == null
            || ingredient.rootAllergen() == null
            || ingredient.chemicalAlias();
    }

    // Format the nutrient value
    private String nutrientValue(BigDecimal value) {
        return value == null ? MISSING : value.toPlainString();
    }

    // Format the nullable value
    private String nullableValue(String value) {
        return value == null ? MISSING : quote(value);
    }

    // Format the quoted list
    private String quotedList(List<String> values) {
        return values.stream()
            .map(value -> value == null ? MISSING : quote(value))
            .collect(Collectors.joining(",", "[", "]"));
    }

    // Quote the value
    private String quote(String value) {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            + "\"";
    }
}
