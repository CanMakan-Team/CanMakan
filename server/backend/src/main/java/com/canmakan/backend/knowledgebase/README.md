# knowledgebase

Ingredient and dietary-rule knowledge used by the verdict engine and the in-process assess agent.

## Purpose

Central store of aliases, allergen relationships, E-numbers, and dietary rules. Knowledge is **loaded from MySQL** at startup (`@PostConstruct` on [`DietaryKnowledgeRepository`](repository/DietaryKnowledgeRepository.java)), not from JSON/YAML files. Seed data: [`02_ingredients.sql`](../../../../../resources/02_ingredients.sql) and related SQL under `src/main/resources`.

## Layout

| Path | Role |
| --- | --- |
| [`repository/DietaryKnowledgeRepository.java`](repository/DietaryKnowledgeRepository.java) | In-memory store filled from DB |
| [`mcp/DietaryKnowledgeMcpClient.java`](mcp/DietaryKnowledgeMcpClient.java) | Client / `IngredientResolver` boundary |
| [`mcp/server/DietaryKnowledgeMcpServer.java`](mcp/server/DietaryKnowledgeMcpServer.java) | Registers the five MCP-style tools |
| [`restriction/JpaIngredientRestrictionLookup.java`](restriction/JpaIngredientRestrictionLookup.java) | Ingredient ↔ restriction mappings |

## MCP tools

In-process Spring beans (not a remote MCP protocol server): alias lookup, allergen relationship, E-number, dietary rule, cross-contamination. Primary consumers: [`product` verdict](../product/README.md) and [`ai`](../ai/README.md).
