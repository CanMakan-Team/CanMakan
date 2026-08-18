package com.canmakan.backend.product.recommendation.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
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

    public static boolean containsAnyIgnoreCase(Set<String> tags, Collection<String> needles) {
        if (tags.isEmpty() || needles == null || needles.isEmpty()) {
            return false;
        }
        Set<String> normalizedTags = tags.stream()
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String needle : needles) {
            if (needle != null && normalizedTags.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsTag(String categoryTags, String needle) {
        return parseTags(categoryTags).contains(needle);
    }

    /**
     * True when {@code needles} match {@code category_tags} or the OFF-style slug of
     * {@code main_category_en} (e.g. {@code Brown Rice Flour} → {@code en:brown-rice-flour}).
     */
    public static boolean containsAnyIncludingMainCategory(
            String categoryTags,
            String mainCategoryEn,
            Collection<String> needles) {
        if (containsAny(parseTags(categoryTags), needles)) {
            return true;
        }
        if (needles == null || needles.isEmpty() || mainCategoryEn == null || mainCategoryEn.isBlank()) {
            return false;
        }
        String slug = toCategoryTag(mainCategoryEn);
        for (String needle : needles) {
            if (needle != null && needle.equalsIgnoreCase(slug)) {
                return true;
            }
        }
        return false;
    }

    public static String toCategoryTag(String mainCategoryEn) {
        // Any run of non-alphanumeric characters collapses to a single dash, so the result
        // can never contain consecutive dashes; stripping at most one from each end (instead
        // of a "-+" regex trim) is equivalent and avoids a super-linear regex pattern.
        String slug = mainCategoryEn.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        if (slug.startsWith("-")) {
            slug = slug.substring(1);
        }
        if (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return "en:" + slug;
    }
}
