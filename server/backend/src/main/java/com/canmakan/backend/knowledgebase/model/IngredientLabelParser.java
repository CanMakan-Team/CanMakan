package com.canmakan.backend.knowledgebase.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits ingredient-label text on commas that sit outside parentheses, and rejoins
 * fragments that were already split inside a parenthetical list.
 *
 * <p>Naive {@code split(",")} turns {@code Oyster Extract (Oysters, Water, Salt)} into
 * three labels and sends Water/Salt to external search. Depth-aware splitting keeps
 * that group as one label.
 */
public final class IngredientLabelParser {

    private IngredientLabelParser() {
    }

    /**
     * Flatten and repair a list of labels: split any entry that still contains
     * top-level commas, then rejoin fragments whose parentheses are unbalanced.
     */
    public static List<String> normalize(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        List<String> flattened = new ArrayList<>();
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            flattened.addAll(split(label));
        }
        return coalesceUnbalancedParentheses(flattened);
    }

    /**
     * Split a comma-separated ingredient line without breaking parenthetical lists.
     */
    public static List<String> split(String ingredientText) {
        if (ingredientText == null || ingredientText.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < ingredientText.length(); i++) {
            char character = ingredientText.charAt(i);
            if (character == '(') {
                depth++;
                current.append(character);
            } else if (character == ')') {
                if (depth > 0) {
                    depth--;
                }
                current.append(character);
            } else if (character == ',' && depth == 0) {
                addTrimmed(parts, current);
            } else {
                current.append(character);
            }
        }
        addTrimmed(parts, current);
        return List.copyOf(parts);
    }

    static List<String> coalesceUnbalancedParentheses(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return List.of();
        }
        List<String> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int depth = 0;
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append(", ");
            }
            buffer.append(part.trim());
            depth += parenthesisDelta(part);
            if (depth <= 0) {
                merged.add(buffer.toString());
                buffer.setLength(0);
                depth = 0;
            }
        }
        if (buffer.length() > 0) {
            merged.add(buffer.toString());
        }
        return List.copyOf(merged);
    }

    private static int parenthesisDelta(String value) {
        int delta = 0;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '(') {
                delta++;
            } else if (character == ')') {
                delta--;
            }
        }
        return delta;
    }

    private static void addTrimmed(List<String> parts, StringBuilder current) {
        String token = current.toString().trim();
        if (!token.isEmpty()) {
            parts.add(token);
        }
        current.setLength(0);
    }
}
