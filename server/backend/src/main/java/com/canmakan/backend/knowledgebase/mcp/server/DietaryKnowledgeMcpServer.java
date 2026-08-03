package com.canmakan.backend.knowledgebase.mcp.server;

import org.springframework.context.annotation.Configuration;

/**
 * Server side of the Dietary Knowledge MCP boundary (MW). Registers the five
 * knowledge lookup tools so the {@code DietaryKnowledgeMcpClient} can call them
 * over MCP.
 *
 * @author Amelia Wong
 */
@Configuration
public class DietaryKnowledgeMcpServer {

    // TODO: register the five tools as Spring AI MCP server tool callbacks
    //       (ingredient alias, E-number, allergen relationship, dietary rule,
    //        cross-contamination). e.g. a ToolCallbackProvider bean.
}
