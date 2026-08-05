package com.canmakan.backend.knowledgebase.model;

/**
 * Represents a dietary rule definition.
 */
public record DietaryRule(
        String code,
        String category,
        String description
) {
}
