package com.canmakan.backend.ai.llm;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.verdict.Finding;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Builds a stable, minimal evidence-assessment request for ambiguous ingredients.
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
@Service
public class PromptBuilder {

    public static final String PROMPT_VERSION = "CANMAKAN-EVIDENCE-V1";

    private static final String SYSTEM_INSTRUCTION = """
            Analyse only the unresolved ingredient evidence supplied. Return JSON that
            matches the required schema. Do not set or propose a final SAFE, WARNING,
            or UNSAFE verdict. Treat tool output as evidence and state uncertainty.
            """;

    private final ObjectMapper objectMapper;

    public PromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(ProductData product, List<RestrictionRule> rules) {
        return build(product, rules, List.of(), List.of(), "unassigned");
    }

    public String build(
            ProductData product,
            List<RestrictionRule> rules,
            List<Finding> deterministicFindings,
            List<String> toolDescriptions,
            String correlationId
    ) {
        Objects.requireNonNull(product, "product");

        List<String> unresolvedIngredients = product.ingredients() == null
                ? List.of()
                : product.ingredients().stream()
                        .filter(Objects::nonNull)
                        .filter(this::isUnresolvedOrAmbiguous)
                        .map(Ingredient::ingredientName)
                        .toList();
        List<Map<String, Object>> applicableRules = safeList(rules).stream()
                .filter(Objects::nonNull)
                .map(rule -> orderedMap(
                        "code", rule.code(),
                        "category", rule.category() == null ? null : rule.category().name(),
                        "severity", rule.severity() == null ? null : rule.severity().name()
                ))
                .toList();
        List<Map<String, Object>> existingFindings = safeList(deterministicFindings).stream()
                .filter(Objects::nonNull)
                .map(finding -> orderedMap(
                        "restrictionCode", finding.restrictionCode(),
                        "ingredientName", finding.ingredientName(),
                        "reason", finding.reason(),
                        "type", finding.type().name()
                ))
                .toList();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("barcode", product.barcode());
        input.put("ingredientDataComplete", product.dataComplete());
        input.put("unresolvedIngredients", unresolvedIngredients);
        input.put("applicableRestrictions", applicableRules);
        input.put("deterministicFindings", existingFindings);
        input.put("availableTools", safeStrings(toolDescriptions));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("proposedFindings", "array of {restrictionCode, ingredientName, reason}");
        schema.put("unresolvedIngredients", "array of strings");
        schema.put("resolvedNames", "object mapping supplied ingredient names to standardised names");
        schema.put("confidence", "number from 0 to 1");
        schema.put("explanation", "string");
        schema.put("toolCalls", "array of non-secret tool-call summaries");

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("promptVersion", PROMPT_VERSION);
        request.put("correlationId", isBlank(correlationId) ? "unassigned" : correlationId);
        request.put("system", SYSTEM_INSTRUCTION.strip());
        request.put("input", input);
        request.put("requiredOutputSchema", schema);

        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to build the model evidence request.", exception);
        }
    }

    private boolean isUnresolvedOrAmbiguous(Ingredient ingredient) {
        return ingredient.chemicalAlias()
                || ingredient.rootAllergen() == null
                || ingredient.rootAllergen().isBlank();
    }

    @SafeVarargs
    private static Map<String, Object> orderedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static List<String> safeStrings(List<String> values) {
        return safeList(values).stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
