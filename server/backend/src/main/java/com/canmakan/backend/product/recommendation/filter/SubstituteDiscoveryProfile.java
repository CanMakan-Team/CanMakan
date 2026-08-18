package com.canmakan.backend.product.recommendation.filter;

import java.util.List;

/**
 * Tag-based substitute discovery rules for a source {@code main_category_en}.
 *
 * @param includeTags primary {@code category_tags} values queried in Tier A tag discovery
 * @param secondaryIncludeTags additional {@code category_tags} for Tier C expansion and ranking boosts
 *        (e.g. plant-milk drink types, specialty GF flour types)
 * @param deprioritizeTags substitute tags that should rank lower (e.g. cooking creams vs drinking milks)
 */
public record SubstituteDiscoveryProfile(
        List<String> includeTags,
        List<String> secondaryIncludeTags,
        List<String> deprioritizeTags,
        List<String> labelTags,
        List<String> siblingCategories,
        List<String> excludeCategoryTags,
        List<String> excludeTracesTags
) {
    public SubstituteDiscoveryProfile(
            List<String> includeTags,
            List<String> secondaryIncludeTags,
            List<String> deprioritizeTags,
            List<String> labelTags,
            List<String> siblingCategories) {
        this(includeTags, secondaryIncludeTags, deprioritizeTags, labelTags, siblingCategories, List.of(), List.of());
    }

    public SubstituteDiscoveryProfile(
            List<String> includeTags,
            List<String> secondaryIncludeTags,
            List<String> deprioritizeTags) {
        this(includeTags, secondaryIncludeTags, deprioritizeTags, List.of(), List.of(), List.of(), List.of());
    }
}
