package com.canmakan.backend.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.knowledgebase.model.RestrictionCategory;
import com.canmakan.backend.product.verdict.Finding;
import com.canmakan.backend.product.verdict.FindingType;
import com.canmakan.backend.product.verdict.ProductData;
import com.canmakan.backend.product.verdict.RestrictionRule;
import com.canmakan.backend.product.verdict.RestrictionSeverity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests stable and minimal model evidence request construction.
 *
 * @author YangMaowei
 */
class PromptBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PromptBuilder builder = new PromptBuilder(objectMapper);

    @Test
    void buildsVersionedStructuredRequestFromOnlyAmbiguousIngredients() throws Exception {
        ProductData product = new ProductData(
                "123",
                List.of(
                        new Ingredient("Known Milk", null, "DAIRY", false),
                        new Ingredient("Unknown Additive", null, null, false),
                        new Ingredient("E471", null, "OTHER", true)
                ),
                "Unnecessary full ingredient text",
                List.of("en:halal"),
                null,
                false
        );
        RestrictionRule rule = new RestrictionRule(
                "VEGAN", RestrictionCategory.DIET, RestrictionSeverity.STRICT_AVOID
        );
        Finding finding = new Finding(
                "VEGAN",
                null,
                "Ingredient data is incomplete.",
                FindingType.INCOMPLETE_DATA
        );

        String compiled = builder.build(
                product,
                List.of(rule),
                List.of(finding),
                List.of("ingredient_alias_lookup: standardises an ingredient name"),
                "correlation-123"
        );
        JsonNode root = objectMapper.readTree(compiled);

        assertEquals(PromptBuilder.PROMPT_VERSION, root.path("promptVersion").asText());
        assertEquals("correlation-123", root.path("correlationId").asText());
        assertEquals("123", root.path("input").path("barcode").asText());
        assertEquals(2, root.path("input").path("unresolvedIngredients").size());
        assertEquals("Unknown Additive",
                root.path("input").path("unresolvedIngredients").get(0).asText());
        assertEquals("VEGAN",
                root.path("input").path("applicableRestrictions").get(0).path("code").asText());
        assertEquals("INCOMPLETE_DATA",
                root.path("input").path("deterministicFindings").get(0).path("type").asText());
        assertTrue(root.path("requiredOutputSchema").has("proposedFindings"));
        assertFalse(compiled.contains("Known Milk"));
        assertFalse(compiled.contains("Unnecessary full ingredient text"));
        assertFalse(compiled.contains("en:halal"));
    }

    @Test
    void producesIdenticalOutputForIdenticalInput() {
        ProductData product = new ProductData(
                "123",
                List.of(new Ingredient("Unknown", null, null, false)),
                null,
                List.of(),
                null,
                false
        );

        String first = builder.build(product, List.of());
        String second = builder.build(product, List.of());

        assertEquals(first, second);
    }
}
