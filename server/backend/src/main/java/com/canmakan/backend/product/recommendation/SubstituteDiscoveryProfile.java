package com.canmakan.backend.product.recommendation;

import java.util.List;

/**
 * Tag-based substitute discovery rules for a source {@code main_category_en}.
 */
public record SubstituteDiscoveryProfile(
        List<String> includeTags,
        List<String> beverageTags,
        List<String> deprioritizeTags
) {
}
