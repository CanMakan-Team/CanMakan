package com.canmakan.backend.knowledgebase.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.canmakan.backend.knowledgebase.mcp.contract.AllergenRelationshipResult;
import com.canmakan.backend.knowledgebase.mcp.contract.ENumberResult;
import com.canmakan.backend.knowledgebase.mcp.contract.IngredientAliasResult;
import com.canmakan.backend.knowledgebase.mcp.server.AllergenRelationshipTool;
import com.canmakan.backend.knowledgebase.mcp.server.CrossContaminationTool;
import com.canmakan.backend.knowledgebase.mcp.server.DietaryRuleTool;
import com.canmakan.backend.knowledgebase.mcp.server.ENumberTool;
import com.canmakan.backend.knowledgebase.mcp.server.IngredientAliasTool;
import com.canmakan.backend.knowledgebase.model.Ingredient;
import com.canmakan.backend.product.verdict.IngredientResolution;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link DietaryKnowledgeMcpClient}: the alias-then-hierarchy resolution
 * logic and delegation to the in-process tools.
 *
 * @author XieHuayuan
 * @author Amelia
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UC3: DietaryKnowledgeMcpClient - Alias-then-hierarchy resolution logic and delegation to the in-process tools")
class DietaryKnowledgeMcpClientTest {

    @Mock
    private IngredientAliasTool ingredientAliasTool;
    @Mock
    private ENumberTool eNumberTool;
    @Mock
    private AllergenRelationshipTool allergenRelationshipTool;
    @Mock
    private DietaryRuleTool dietaryRuleTool;
    @Mock
    private CrossContaminationTool crossContaminationTool;

    @InjectMocks
    private DietaryKnowledgeMcpClient client;

    @Test
    void aliasRootAllergenIsReturnedWithoutFallback() {
        when(ingredientAliasTool.lookup("whey"))
                .thenReturn(new IngredientAliasResult("whey", "Whey", "DAIRY", false, true));

        assertEquals("DAIRY", client.resolveRootAllergen("whey"));
        assertEquals(IngredientResolution.Kind.RESOLVED, client.resolve("whey").kind());
        verifyNoInteractions(allergenRelationshipTool);
    }

    @Test
    void fallsBackToAllergenHierarchyWhenAliasHasNoRoot() {
        when(ingredientAliasTool.lookup("casein"))
                .thenReturn(new IngredientAliasResult("casein", "Casein", null, false, true));
        when(allergenRelationshipTool.lookup("Casein"))
                .thenReturn(new AllergenRelationshipResult(
                        List.of(new Ingredient("Casein", "Milk", "DAIRY", false)),
                        List.of(), "", List.of()));

        assertEquals("DAIRY", client.resolveRootAllergen("casein"));
    }

    @Test
    void catalogHitWithoutRootIsKnownSafe() {
        when(ingredientAliasTool.lookup("sugar"))
                .thenReturn(new IngredientAliasResult("sugar", "Sugar", null, false, true));
        when(allergenRelationshipTool.lookup("Sugar"))
                .thenReturn(new AllergenRelationshipResult(List.of(), List.of("Sugar"), "", List.of()));

        assertEquals(IngredientResolution.Kind.KNOWN_SAFE, client.resolve("sugar").kind());
        assertNull(client.resolveRootAllergen("sugar"));
    }

    @Test
    void returnsUnknownWhenNeitherAliasNorHierarchyResolves() {
        when(ingredientAliasTool.lookup("mystery"))
                .thenReturn(new IngredientAliasResult("mystery", "mystery", null, false, false));
        when(allergenRelationshipTool.lookup("mystery"))
                .thenReturn(new AllergenRelationshipResult(List.of(), List.of("mystery"), "", List.of()));

        assertEquals(IngredientResolution.Kind.UNKNOWN, client.resolve("mystery").kind());
        assertNull(client.resolveRootAllergen("mystery"));
    }

    @Test
    void returnsNullForBlankOrNullInputWithoutCallingTools() {
        assertNull(client.resolveRootAllergen("   "));
        assertNull(client.resolveRootAllergen(null));
        verifyNoInteractions(ingredientAliasTool, allergenRelationshipTool);
    }

    @Test
    void usesExternalMatchesWhenLocalHierarchyMisses() {
        when(ingredientAliasTool.lookup("casein"))
                .thenReturn(new IngredientAliasResult("casein", "casein", null, false, false));
        when(allergenRelationshipTool.lookup("casein"))
                .thenReturn(new AllergenRelationshipResult(
                        List.of(),
                        List.of("casein"),
                        "casein -> DAIRY",
                        List.of(new Ingredient("casein", null, "DAIRY", false))));

        assertEquals(IngredientResolution.Kind.RESOLVED, client.resolve("casein").kind());
        assertEquals("DAIRY", client.resolveRootAllergen("casein"));
    }

    @Test
    void externalNoneIsKnownSafe() {
        when(ingredientAliasTool.lookup("inulin"))
                .thenReturn(new IngredientAliasResult("inulin", "inulin", null, false, false));
        when(allergenRelationshipTool.lookup("inulin"))
                .thenReturn(new AllergenRelationshipResult(
                        List.of(),
                        List.of("inulin"),
                        "inulin -> NONE",
                        List.of(new Ingredient("inulin", null, "NONE", false))));

        assertEquals(IngredientResolution.Kind.KNOWN_SAFE, client.resolve("inulin").kind());
        assertNull(client.resolveRootAllergen("inulin"));
    }

    @Test
    void eNumberLookupResolvesAdditiveWhenAliasAndHierarchyMiss() {
        when(ingredientAliasTool.lookup("E471"))
                .thenReturn(new IngredientAliasResult("E471", "E471", null, false, false));
        when(allergenRelationshipTool.lookup("E471"))
                .thenReturn(new AllergenRelationshipResult(List.of(), List.of("E471"), "", List.of()));
        when(eNumberTool.lookup("E471"))
                .thenReturn(new ENumberResult("E471", "Mono- and diglycerides", "Emulsifiers", "ADDITIVE", false));

        IngredientResolution resolution = client.resolve("E471");
        assertEquals(IngredientResolution.Kind.RESOLVED, resolution.kind());
        assertEquals("ADDITIVE", resolution.rootAllergen());
        assertEquals("Mono- and diglycerides", resolution.canonicalName());
        assertTrue(resolution.chemicalAlias());
        assertEquals("ADDITIVE", client.resolveRootAllergen("E471"));
    }

    @Test
    void eNumberLookupPrefersCatalogRootWhenAnimalDerived() {
        when(ingredientAliasTool.lookup("E1105"))
                .thenReturn(new IngredientAliasResult("E1105", "E1105", null, false, false));
        when(allergenRelationshipTool.lookup("E1105"))
                .thenReturn(new AllergenRelationshipResult(List.of(), List.of("E1105"), "", List.of()));
        when(eNumberTool.lookup("E1105"))
                .thenReturn(new ENumberResult(
                        "E1105",
                        "E1105 (Lysozyme from eggs)",
                        "Egg Derivatives",
                        "EGG",
                        true));

        IngredientResolution resolution = client.resolve("E1105");
        assertEquals("EGG", resolution.rootAllergen());
        assertEquals("E1105 (Lysozyme from eggs)", resolution.canonicalName());
        assertTrue(resolution.chemicalAlias());
    }

    @Test
    void eNumberAnimalDerivedAdditiveMapsToMeat() {
        when(ingredientAliasTool.lookup("E631"))
                .thenReturn(new IngredientAliasResult("E631", "E631", null, false, false));
        when(allergenRelationshipTool.lookup("E631"))
                .thenReturn(new AllergenRelationshipResult(List.of(), List.of("E631"), "", List.of()));
        when(eNumberTool.lookup("E631"))
                .thenReturn(new ENumberResult(
                        "E631",
                        "E631 (Disodium Inosinate)",
                        "Flavor Enhancers",
                        "ADDITIVE",
                        true));

        assertEquals("MEAT", client.resolveRootAllergen("E631"));
    }

    @Test
    void unknownENumberDoesNotForceResolution() {
        when(ingredientAliasTool.lookup("E999"))
                .thenReturn(new IngredientAliasResult("E999", "E999", null, false, false));
        when(allergenRelationshipTool.lookup("E999"))
                .thenReturn(new AllergenRelationshipResult(List.of(), List.of("E999"), "", List.of()));
        when(eNumberTool.lookup("E999"))
                .thenReturn(new ENumberResult("E999", "Unknown additive", "unknown", "", false));

        assertEquals(IngredientResolution.Kind.UNKNOWN, client.resolve("E999").kind());
    }

    @Test
    void lookupMethodsDelegateToTheirTools() {
        ENumberResult eNumber = new ENumberResult("E471", "Mono- and diglycerides", "emulsifier", "ADDITIVE", true);
        when(eNumberTool.lookup("E471")).thenReturn(eNumber);

        assertSame(eNumber, client.lookupENumber("E471"));
    }

    @Test
    void resolveAllBatchesTheAllergenLookupAndKeepsPerNameOutcomes() {
        when(ingredientAliasTool.lookup("whey"))
                .thenReturn(new IngredientAliasResult("whey", "Whey", null, false, true));
        when(ingredientAliasTool.lookup("mystery"))
                .thenReturn(new IngredientAliasResult("mystery", "mystery", null, false, false));
        when(allergenRelationshipTool.lookup(List.of("Whey", "mystery")))
                .thenReturn(new AllergenRelationshipResult(
                        List.of(new Ingredient("Whey", "Milk", "DAIRY", false)),
                        List.of("mystery"), "", List.of()));

        Map<String, IngredientResolution> resolutions =
                client.resolveAll(List.of("whey", "mystery"));

        assertEquals(IngredientResolution.Kind.RESOLVED, resolutions.get("whey").kind());
        assertEquals("DAIRY", resolutions.get("whey").rootAllergen());
        // The batched Whey match must not leak onto the unrelated, unresolved label.
        assertEquals(IngredientResolution.Kind.UNKNOWN, resolutions.get("mystery").kind());
    }
}
