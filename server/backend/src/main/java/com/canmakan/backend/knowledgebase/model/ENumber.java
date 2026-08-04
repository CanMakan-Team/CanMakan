package com.canmakan.backend.knowledgebase.model;

/**
 * Represents a food additive E-number entry.
 */
public record ENumber(
        String eNumber,
        String name,
        String category,
        boolean animalDerived
) {
}
