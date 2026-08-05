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
import java.util.List;
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
    void aliasRootAllergenIsReturnedWithoutFallback() {
        when(ingredientAliasTool.lookup("whey"))
                .thenReturn(new IngredientAliasResult("whey", "Whey", "DAIRY", false));

        assertEquals("DAIRY", client.resolveRootAllergen("whey"));
        verifyNoInteractions(allergenRelationshipTool);
    }

    @Test
    void fallsBackToAllergenHierarchyWhenAliasHasNoRoot() {
        when(ingredientAliasTool.lookup("casein"))
                .thenReturn(new IngredientAliasResult("casein", "Casein", null, false));
        when(allergenRelationshipTool.lookup("Casein"))
                .thenReturn(new AllergenRelationshipResult(
                        List.of(new Ingredient("Casein", "Milk", "DAIRY", false)),
                        List.of(), "", List.of()));

        assertEquals("DAIRY", client.resolveRootAllergen("casein"));
    }

    @Test
    void returnsNullWhenNeitherAliasNorHierarchyResolves() {
        when(ingredientAliasTool.lookup("sugar"))
                .thenReturn(new IngredientAliasResult("sugar", "sugar", null, false));
        when(allergenRelationshipTool.lookup("sugar"))
                .thenReturn(new AllergenRelationshipResult(List.of(), List.of("sugar"), "", List.of()));

        assertNull(client.resolveRootAllergen("sugar"));
    }

    @Test
    void returnsNullForBlankOrNullInputWithoutCallingTools() {
        assertNull(client.resolveRootAllergen("   "));
        assertNull(client.resolveRootAllergen(null));
        verifyNoInteractions(ingredientAliasTool, allergenRelationshipTool);
    }

    @Test
    void lookupMethodsDelegateToTheirTools() {
        ENumberResult eNumber = new ENumberResult("E471", "Mono- and diglycerides", "emulsifier", true);
        when(eNumberTool.lookup("E471")).thenReturn(eNumber);

        assertSame(eNumber, client.lookupENumber("E471"));
    }
}
