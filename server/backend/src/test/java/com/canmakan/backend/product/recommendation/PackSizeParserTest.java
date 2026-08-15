package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UC5: PackSizeParser")
class PackSizeParserTest {

    @Test
    void parsesLitresAndMillilitres() {
        assertEquals(1000.0, PackSizeParser.parseVolumeMl("1 l").orElseThrow());
        assertEquals(1000.0, PackSizeParser.parseVolumeMl("1 Litre").orElseThrow());
        assertEquals(375.0, PackSizeParser.parseVolumeMl("375ml").orElseThrow());
        assertEquals(250.0, PackSizeParser.parseVolumeMl("250 ml").orElseThrow());
    }

    @Test
    void resolvesQuantityBeforeServingFields() {
        CatalogProduct product = new CatalogProduct();
        product.setQuantity("1 l");
        product.setServingSize("250 ml");
        product.setServingQuantity(new BigDecimal("250"));

        assertEquals(1000.0, PackSizeParser.resolveVolumeMl(product).orElseThrow());
    }

    @Test
    void similarityPrefersMatchingPackVolume() {
        CatalogProduct oneLitreSource = productWithQuantity("src", "1 l");
        CatalogProduct oneLitreCandidate = productWithQuantity("one", "1 Litre");
        CatalogProduct smallCandidate = productWithQuantity("small", "375 ml");

        assertTrue(PackSizeParser.similarity(oneLitreSource, oneLitreCandidate) > 0.85);
        assertTrue(PackSizeParser.similarity(oneLitreSource, oneLitreCandidate)
                > PackSizeParser.similarity(oneLitreSource, smallCandidate));
    }

    private static CatalogProduct productWithQuantity(String barcode, String quantity) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setQuantity(quantity);
        return product;
    }
}
