package com.canmakan.backend.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.model.Nutrition;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.RestrictionSeverity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests deterministic and data-minimised evidence prompt construction.
 *
 * @author YangMaowei
 */
class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void producesIdenticalPromptWithoutChangingInputLists() {
        List<Ingredient> ingredients = new ArrayList<>(List.of(unmappedIngredient()));
        List<String> labelTags = new ArrayList<>(List.of("en:halal"));
        List<RestrictionRule> rules = new ArrayList<>(List.of(halalRule()));
        ProductData product = product(ingredients, labelTags, nutrition(null, BigDecimal.ZERO));

        String first = promptBuilder.build(product, rules);
        String second = promptBuilder.build(product, rules);

        assertEquals(first, second);
        assertEquals(List.of(unmappedIngredient()), ingredients);
        assertEquals(List.of("en:halal"), labelTags);
        assertEquals(List.of(halalRule()), rules);
    }

    @Test
    void includesVersionRestrictionAndNecessaryProductEvidence() {
        String prompt = promptBuilder.build(
            product(
                List.of(unmappedIngredient()),
                List.of("en:halal"),
                nutrition(null, BigDecimal.ZERO)
            ),
            List.of(halalRule())
        );

        assertTrue(prompt.contains("PROMPT_VERSION: canmakan-evidence-v1"));
        assertTrue(prompt.contains("barcode=\"8888888888888\""));
        assertTrue(prompt.contains("ingredientsText=\"Mystery powder, sugar\""));
        assertTrue(prompt.contains("labelTags=[\"en:halal\"]"));
        assertTrue(prompt.contains("code=\"HALAL\""));
        assertTrue(prompt.contains("category=RELIGIOUS"));
        assertTrue(prompt.contains("ingredientName=\"Mystery powder\""));
    }

    @Test
    void requestsThreeFieldFindingsAndForbidsAuthoritativeVerdict() {
        String prompt = standardPrompt();

        assertTrue(prompt.contains("\"restrictionCode\":\"string|null\""));
        assertTrue(prompt.contains("\"ingredientName\":\"string|null\""));
        assertTrue(prompt.contains("\"reason\":\"string\""));
        assertTrue(prompt.contains(
            "Each finding must contain exactly restrictionCode, ingredientName, and reason."
        ));
        assertTrue(prompt.contains("Do not decide or output SAFE, WARNING, or UNSAFE."));
        assertTrue(prompt.contains("Do not output a verdict field."));
    }

    @Test
    void requiresUncertaintyAndRejectsFabricatedOrUnsafeAssumptions() {
        String prompt = standardPrompt();

        assertTrue(prompt.contains("Do not fabricate evidence."));
        assertTrue(prompt.contains("State uncertainty"));
        assertTrue(prompt.contains("Do not infer missing nutrition as zero."));
        assertTrue(prompt.contains("Do not treat an unmapped ingredient as safe."));
        assertFalse(prompt.contains("ingredientName=\"Mystery powder\", classification=safe"));
    }

    @Test
    void doesNotIncludeIdentityCredentialsOrConfiguration() {
        String prompt = standardPrompt();

        assertFalse(prompt.contains("userId"));
        assertFalse(prompt.contains("OPENAI_API_KEY"));
        assertFalse(prompt.contains("Authorization:"));
        assertFalse(prompt.contains("Bearer "));
        assertFalse(prompt.contains("spring.datasource"));
    }

    @Test
    void describesNullNutritionAsMissingRatherThanZero() {
        String prompt = promptBuilder.build(
            product(List.of(unmappedIngredient()), List.of(), nutrition(null, BigDecimal.ONE)),
            List.of(halalRule())
        );

        assertTrue(prompt.contains("sugarsPer100g=MISSING_OR_UNKNOWN"));
        assertFalse(prompt.contains("sugarsPer100g=0"));
    }

    @Test
    void preservesConfirmedZeroRatherThanDescribingItAsMissing() {
        String prompt = standardPrompt();

        assertTrue(prompt.contains("sodiumPer100g=0"));
        assertFalse(prompt.contains("sodiumPer100g=MISSING_OR_UNKNOWN"));
    }

    @Test
    void rejectsNullAndInvalidInputs() {
        ProductData validProduct = product(
            List.of(unmappedIngredient()),
            List.of(),
            null
        );

        assertThrows(NullPointerException.class, () -> promptBuilder.build(null, List.of()));
        assertThrows(NullPointerException.class, () -> promptBuilder.build(validProduct, null));
        assertThrows(
            IllegalArgumentException.class,
            () -> promptBuilder.build(
                new ProductData(" ", List.of(), null, List.of(), null, false),
                List.of()
            )
        );
        assertThrows(
            NullPointerException.class,
            () -> promptBuilder.build(
                validProduct,
                java.util.Collections.singletonList(null)
            )
        );
    }

    private String standardPrompt() {
        return promptBuilder.build(
            product(
                List.of(unmappedIngredient()),
                List.of("en:halal"),
                nutrition(null, BigDecimal.ZERO)
            ),
            List.of(halalRule())
        );
    }

    private static ProductData product(
        List<Ingredient> ingredients,
        List<String> labelTags,
        Nutrition nutrition
    ) {
        return new ProductData(
            "8888888888888",
            ingredients,
            "Mystery powder, sugar",
            labelTags,
            nutrition,
            false
        );
    }

    private static Nutrition nutrition(BigDecimal sugars, BigDecimal sodium) {
        return new Nutrition(sugars, sodium, null, null, null, null);
    }

    private static Ingredient unmappedIngredient() {
        return new Ingredient("Mystery powder", null, null, false);
    }

    private static RestrictionRule halalRule() {
        return new RestrictionRule(
            "HALAL",
            RestrictionCategory.RELIGIOUS,
            RestrictionSeverity.STRICT_AVOID
        );
    }
}
