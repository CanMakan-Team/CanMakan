package com.canmakan.backend.product.assessment;

import com.canmakan.backend.integration.BarcodeValidationClient;
import com.canmakan.backend.product.model.ProductLookupResult;
import com.canmakan.backend.product.verdict.ProductData;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Adapts a source-neutral product lookup result into the immutable snapshot used
 * by the deterministic verdict engine.
 *
 * @author XieHuayuan
 * @author YangMaowei
 * @author Amelia
 */
@Service
public class ProductDataAdapter {

    private final BarcodeValidationClient barcodeValidationClient;

    public ProductDataAdapter(BarcodeValidationClient barcodeValidationClient) {
        this.barcodeValidationClient = Objects.requireNonNull(
            barcodeValidationClient, "barcodeValidationClient");
    }

    /**
     * Looks up a product by barcode without applying dietary rules.
     *
     * @param barcode the scanned barcode
     * @return the source-neutral lookup result
     */
    public ProductLookupResult lookup(String barcode) {
        return barcodeValidationClient.fetchProduct(barcode);
    }

    /**
     * Maps the lookup result without applying dietary rules or interpreting data.
     * The ProductData object is used to store the product data in the database.
     *
     * @param result the source-neutral product lookup result
     * @return the product snapshot expected by the verdict engine
     */
    public ProductData toProductData(ProductLookupResult result) {
        Objects.requireNonNull(result, "result must not be null");

        return new ProductData(
                result.barcode(),
                result.ingredients(),
                result.ingredientsText(),
                toLabelTags(result.labelTags()),
                result.nutrition(),
                result.ingredientDataComplete()
        );
    }

    /**
     * Assemble the {@link ProductData} the engine needs for one barcode.
     * Overloaded method to accept a barcode string instead of a ProductLookupResult.
     * Only maps the lookup result without applying dietary rules or interpreting data.
     *
     * @param barcode the scanned barcode
     * @return a populated {@link ProductData}; {@code dataComplete=false} when partial
     */
    public ProductData toProductData(String barcode) {
        return toProductData(lookup(barcode));
    }

    private List<String> toLabelTags(String labelTags) {
        if (labelTags == null) {
            return List.of();
        }

        return Arrays.stream(labelTags.split(","))
                .map(String::trim)
                .filter(label -> !label.isEmpty())
                .distinct()
                .toList();
    }
}
