package com.canmakan.backend.product.recommendation.filter;

import com.canmakan.backend.product.recommendation.catalog.CatalogProduct;
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
        assertTrue(PackSizeParser.isStrongPackSizeMatch(oneLitreSource, oneLitreCandidate));
        assertEquals(
                PackSizeParser.similarity(oneLitreSource, oneLitreCandidate) * PackSizeParser.PACK_SIZE_WEIGHT,
                PackSizeParser.weightedBoost(oneLitreSource, oneLitreCandidate));
    }

    @Test
    void parseVolumeMlHandlesMissingDigitsUnitsAndDotOnlyAmounts() {
        assertTrue(PackSizeParser.parseVolumeMl(null).isEmpty());
        assertTrue(PackSizeParser.parseVolumeMl("  ").isEmpty());
        assertTrue(PackSizeParser.parseVolumeMl("no-digits").isEmpty());
        assertTrue(PackSizeParser.parseVolumeMl(".").isEmpty());
        assertTrue(PackSizeParser.parseVolumeMl("500 g").isEmpty());
        assertTrue(PackSizeParser.parseVolumeMl("500 lb").isEmpty());
        assertEquals(250.0, PackSizeParser.parseVolumeMl("250 millilitre").orElseThrow());
        assertEquals(250.0, PackSizeParser.parseVolumeMl("250 milliliter").orElseThrow());
        assertEquals(250.0, PackSizeParser.parseVolumeMl("25 cl").orElseThrow());
        assertEquals(1000.0, PackSizeParser.parseVolumeMl("1 liter").orElseThrow());
        assertEquals(1500.0, PackSizeParser.parseVolumeMl("1.5 l").orElseThrow());
    }

    @Test
    void resolveVolumeMlFallsBackThroughServingFields() {
        assertTrue(PackSizeParser.resolveVolumeMl(null).isEmpty());

        CatalogProduct servingSizeOnly = new CatalogProduct();
        servingSizeOnly.setServingSize("250 ml");
        assertEquals(250.0, PackSizeParser.resolveVolumeMl(servingSizeOnly).orElseThrow());

        CatalogProduct servingQuantityOnly = new CatalogProduct();
        servingQuantityOnly.setServingQuantity(new BigDecimal("200"));
        assertEquals(200.0, PackSizeParser.resolveVolumeMl(servingQuantityOnly).orElseThrow());

        CatalogProduct invalidServingQuantity = new CatalogProduct();
        invalidServingQuantity.setServingQuantity(BigDecimal.ZERO);
        assertTrue(PackSizeParser.resolveVolumeMl(invalidServingQuantity).isEmpty());

        CatalogProduct oversizedServingQuantity = new CatalogProduct();
        oversizedServingQuantity.setServingQuantity(new BigDecimal("5001"));
        assertTrue(PackSizeParser.resolveVolumeMl(oversizedServingQuantity).isEmpty());
    }

    @Test
    void similarityIsZeroWhenEitherVolumeIsUnknown() {
        CatalogProduct known = productWithQuantity("src", "1 l");
        CatalogProduct unknown = new CatalogProduct();
        unknown.setBarcode("unknown");
        assertEquals(0.0, PackSizeParser.similarity(known, unknown));
        assertEquals(0.0, PackSizeParser.similarity(null, known));
    }

    private static CatalogProduct productWithQuantity(String barcode, String quantity) {
        CatalogProduct product = new CatalogProduct();
        product.setBarcode(barcode);
        product.setQuantity(quantity);
        return product;
    }
}
