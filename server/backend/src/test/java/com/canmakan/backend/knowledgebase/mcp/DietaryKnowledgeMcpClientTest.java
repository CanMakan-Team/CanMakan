package com.canmakan.backend.knowledgebase.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import com.canmakan.backend.product.verdict.IngredientResolution.Status;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link DietaryKnowledgeMcpClient}: the tri-state resolution logic
 * (alias -> allergen hierarchy -> E-number) and delegation to the in-process tools.
 *
 * @author XieHuayuan
 */
@ExtendWith(MockitoExtension.class)
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
    void aliasRootAllergenResolvesImmediately() {
        when(ingredientAliasTool.lookup("whey"))
                .thenReturn(new IngredientAliasResult("whey", "Whey", "DAIRY", false));

        IngredientResolution result = client.resolve("whey");

        assertEquals(Status.RESOLVED_ALLERGEN, result.status());
        assertEquals("DAIRY", result.rootAllergen());
        verifyNoInteractions(allergenRelationshipTool, eNumberTool);
    }

    @Test
    void fallsBackToAllergenHierarchyWhenAliasHasNoRoot() {
        when(ingredientAliasTool.lookup("casein"))
                .thenReturn(new IngredientAliasResult("casein", "Casein", null, false));
        when(allergenRelationshipTool.lookup("Casein"))
                .thenReturn(new AllergenRelationshipResult(
                        List.of(new Ingredient("Casein", "Milk", "DAIRY", false)),
                        List.of(), "", List.of()));

        IngredientResolution result = client.resolve("casein");

        assertEquals(Status.RESOLVED_ALLERGEN, result.status());
        assertEquals("DAIRY", result.rootAllergen());
    }

    @Test
    void blankOrNullInputIsUnknownWithoutCallingTools() {
        assertEquals(Status.UNKNOWN, client.resolve("   ").status());
        assertEquals(Status.UNKNOWN, client.resolve(null).status());
        verifyNoInteractions(ingredientAliasTool, allergenRelationshipTool, eNumberTool);
    }

    @Test
    void unknownIngredientWithoutENumberStaysUnknown() {
        when(ingredientAliasTool.lookup("mystery sugar"))
                .thenReturn(new IngredientAliasResult("mystery sugar", "mystery sugar", null, false));
        when(allergenRelationshipTool.lookup("mystery sugar"))
                .thenReturn(new AllergenRelationshipResult(List.of(), List.of("mystery sugar"), "", List.of()));

        assertEquals(Status.UNKNOWN, client.resolve("mystery sugar").status());
    }

    // --- E-number regression / safety cases ------------------------------------

    @Test
    void eNumberAdditiveResolvesToKnownNoAllergen() {
        // "Tricalcium Phosphate e341": alias + hierarchy miss, but the E-number is searched
        // and classified as a recognised, non-animal-derived additive -> no false WARNING.
        when(ingredientAliasTool.lookup("Tricalcium Phosphate e341"))
                .thenReturn(new IngredientAliasResult("Tricalcium Phosphate e341", "Tricalcium Phosphate e341", null, false));
        when(allergenRelationshipTool.lookup("Tricalcium Phosphate e341"))
                .thenReturn(new AllergenRelationshipResult(List.of(), List.of("Tricalcium Phosphate e341"), "", List.of()));
        when(ingredientAliasTool.lookup("E341"))
                .thenReturn(new IngredientAliasResult("E341", "E341", null, false));
        when(eNumberTool.lookup("E341"))
                .thenReturn(new ENumberResult("E341", "Tricalcium phosphate", "acidity regulator", false));

        IngredientResolution result = client.resolve("Tricalcium Phosphate e341");

        assertEquals(Status.KNOWN_NO_ALLERGEN, result.status());
        assertNull(result.rootAllergen());
    }

    @Test
    void allergenLinkedENumberResolvesToAllergen() {
        // E322 (lecithin) can be soy-derived: the alias catalog links the code to SOY,
        // so it must resolve to an allergen, never be marked non-allergen.
        when(ingredientAliasTool.lookup("Emulsifier e322"))
                .thenReturn(new IngredientAliasResult("Emulsifier e322", "Emulsifier e322", null, false));
        when(allergenRelationshipTool.lookup("Emulsifier e322"))
                .thenReturn(new AllergenRelationshipResult(List.of(), List.of("Emulsifier e322"), "", List.of()));
        when(ingredientAliasTool.lookup("E322"))
                .thenReturn(new IngredientAliasResult("E322", "Lecithin", "SOY", true));

        IngredientResolution result = client.resolve("Emulsifier e322");

        assertEquals(Status.RESOLVED_ALLERGEN, result.status());
        assertEquals("SOY", result.rootAllergen());
    }

    @Test
    void animalDerivedENumberStaysUnknownNotSafe() {
        // A recognised but animal-derived additive (e.g. E120 cochineal) must NOT be
        // marked non-allergen; it stays UNKNOWN so the engine keeps caution.
        when(ingredientAliasTool.lookup("Colour e120"))
                .thenReturn(new IngredientAliasResult("Colour e120", "Colour e120", null, false));
        when(allergenRelationshipTool.lookup("Colour e120"))
                .thenReturn(new AllergenRelationshipResult(List.of(), List.of("Colour e120"), "", List.of()));
        when(ingredientAliasTool.lookup("E120"))
                .thenReturn(new IngredientAliasResult("E120", "E120", null, false));
        when(eNumberTool.lookup("E120"))
                .thenReturn(new ENumberResult("E120", "Cochineal", "colour", true));

        assertEquals(Status.UNKNOWN, client.resolve("Colour e120").status());
    }

    @Test
    void lookupMethodsDelegateToTheirTools() {
        ENumberResult eNumber = new ENumberResult("E471", "Mono- and diglycerides", "emulsifier", true);
        when(eNumberTool.lookup("E471")).thenReturn(eNumber);

        assertSame(eNumber, client.lookupENumber("E471"));
    }
}
