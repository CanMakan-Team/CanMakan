package com.canmakan.backend.product.assessment;

import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.verdict.ProductData;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Adapts a source-neutral product lookup result into the immutable product
 * snapshot required by the dietary verdict engine.
 *
 * @author XieHuayuan
 * @author YangMaowei
 */
@Service
public class ProductDataAdapter {

    public ProductData toProductData(ProductLookupResult source) {
        Objects.requireNonNull(source, "source");

        return new ProductData(
                source.barcode(),
                source.ingredients(),
                source.ingredientsText(),
                normalizeLabelTags(source.labelTags()),
                source.nutrition(),
                source.ingredientDataComplete()
        );
    }

    private List<String> normalizeLabelTags(String labelTags) {
        if (labelTags == null || labelTags.isBlank()) {
            return List.of();
        }

        return Arrays.stream(labelTags.split(","))
                .map(String::trim)
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isEmpty())
                .toList();
    }
}
