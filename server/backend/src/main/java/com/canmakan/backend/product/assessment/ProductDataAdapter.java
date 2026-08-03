package com.canmakan.backend.product.assessment;

import com.canmakan.backend.product.verdict.ProductData;
import org.springframework.stereotype.Service;

/**
 * Builds an immutable {@link ProductData} snapshot for the verdict engine from a
 * barcode. Pulls the full product record (ingredients text, label tags, nutrition)
 * and resolves ingredients via the knowledgebase.
 *
 * <p>Note: the teammate's {@code BarcodeValidationClient} only returns
 * {@code validFood}/{@code category}, so this adapter needs a fuller product fetch
 * (products table and/or Open Food Facts full payload).
 *
 * @author XieHuayuan
 */
@Service
public class ProductDataAdapter {

    // TODO: inject product repository / integration client + IngredientResolver.

    /**
     * Assemble the {@link ProductData} the engine needs for one barcode.
     *
     * @param barcode the scanned barcode
     * @return a populated {@link ProductData}; {@code dataComplete=false} when partial
     */
    public ProductData toProductData(String barcode) {
        // TODO: fetch product, map ingredients/labels/nutrition, set dataComplete.
        throw new UnsupportedOperationException("TODO: implement");
    }
}
