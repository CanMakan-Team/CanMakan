package com.canmakan.backend.knowledgebase.mcp.server;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Server side of the Dietary Knowledge MCP boundary (MW). Registers the five
 * knowledge lookup tools so the {@code DietaryKnowledgeMcpClient} can call them
 * over MCP.
 */
@Configuration
public class DietaryKnowledgeMcpServer {

    @Bean
    public ToolCallbackProvider dietaryKnowledgeToolCallbacks(IngredientAliasTool ingredientAliasTool,
        ENumberTool eNumberTool,
        AllergenRelationshipTool allergenRelationshipTool,
        DietaryRuleTool dietaryRuleTool,
        CrossContaminationTool crossContaminationTool) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(ingredientAliasTool, eNumberTool, allergenRelationshipTool, dietaryRuleTool, crossContaminationTool)
            .build();
    }
}
