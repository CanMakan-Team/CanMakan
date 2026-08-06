package com.canmakan.backend.product.verdict;

import org.springframework.stereotype.Component;

/**
 * Temporary no-op resolver used until {@code knowledgebase} / {@code agentic-ai} are wired in.
 * Returning {@link IngredientResolution#unknown()} makes the engine degrade an
 * unresolved ingredient to WARNING rather than emitting a false SAFE.
 *
 * @author XieHuayuan
 */
@Component
public class IngredientResolverStub implements IngredientResolver {

    @Override
    public IngredientResolution resolve(String ingredientName) {
        return IngredientResolution.unknown();   // unknown until the knowledgebase / AI layer resolves it
    }
}
