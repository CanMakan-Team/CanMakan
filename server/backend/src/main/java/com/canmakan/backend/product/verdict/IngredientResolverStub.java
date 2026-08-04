package com.canmakan.backend.product.verdict;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Temporary no-op resolver used until {@code knowledgebase} / {@code agentic-ai} are wired in.
 * Returning {@code null} makes the engine degrade an unresolved ingredient to WARNING
 * rather than emitting a false SAFE.
 *
 * @author XieHuayuan
 */
@Primary // TODO: remove once DietaryKnowledgeMcpClient's tool calls are implemented
@Component
public class IngredientResolverStub implements IngredientResolver {

    @Override
    public String resolveRootAllergen(String ingredientName) {
        return null;   // unknown until the knowledgebase / AI task lands
    }
}
