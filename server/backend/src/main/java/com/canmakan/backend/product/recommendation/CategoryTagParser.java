package com.canmakan.backend.product.recommendation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parses comma-separated Open Food Facts {@code category_tags} values from catalog rows.
 */
public final class CategoryTagParser {

    private CategoryTagParser() {
    }

    public static Set<String> parseTags(String categoryTags) {
        if (categoryTags == null || categoryTags.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(categoryTags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static boolean containsAny(Set<String> tags, Collection<String> needles) {
        if (tags.isEmpty() || needles == null || needles.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            if (tags.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsTag(String categoryTags, String needle) {
        return parseTags(categoryTags).contains(needle);
    }
}
