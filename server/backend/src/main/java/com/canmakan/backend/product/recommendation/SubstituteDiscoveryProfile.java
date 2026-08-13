package com.canmakan.backend.product.recommendation;

import java.util.List;

/**
 * Tag-based substitute discovery rules for a source {@code main_category_en}.
 */
public record SubstituteDiscoveryProfile(
        List<String> includeTags,
        List<String> beverageTags,
        List<String> deprioritizeTags,
        List<String> labelTags,
        List<String> siblingCategories,
        List<String> excludeCategoryTags,
        List<String> excludeTracesTags
) {
    public SubstituteDiscoveryProfile(
            List<String> includeTags,
            List<String> beverageTags,
            List<String> deprioritizeTags,
            List<String> labelTags,
            List<String> siblingCategories) {
        this(includeTags, beverageTags, deprioritizeTags, labelTags, siblingCategories, List.of(), List.of());
    }

    public SubstituteDiscoveryProfile(
            List<String> includeTags,
            List<String> beverageTags,
            List<String> deprioritizeTags) {
        this(includeTags, beverageTags, deprioritizeTags, List.of(), List.of(), List.of(), List.of());
    }
}
