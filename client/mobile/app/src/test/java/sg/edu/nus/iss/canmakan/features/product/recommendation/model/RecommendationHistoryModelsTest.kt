package sg.edu.nus.iss.canmakan.features.product.recommendation.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict

class RecommendationHistoryModelsTest {

    @Test
    fun sourceProductUsesRecordedNameBrandAndBarcode() {
        val entry = sampleEntry(sourceProductName = "Oat Drink", sourceBrand = "Brand", sourceBarcode = "123")
        assertEquals("Oat Drink", entry.sourceProduct().productName)
        assertEquals("Brand", entry.sourceProduct().brand)
        assertEquals("123", entry.sourceProduct().barcode)
    }

    @Test
    fun verdictParsesKnownCodesAndFallsBackToWarning() {
        assertEquals(ScanVerdict.UNSAFE, sampleEntry(sourceVerdict = " unsafe ").verdict())
        assertEquals(ScanVerdict.WARNING, sampleEntry(sourceVerdict = "mystery").verdict())
        assertEquals(ScanVerdict.WARNING, sampleEntry(sourceVerdict = null).verdict())
    }

    @Test
    fun recommendedAtDisplayFormatsBackendTimestampOrKeepsRawValue() {
        assertEquals("1 Aug, 10:00 AM", sampleEntry(recommendedAt = "2026-08-01T10:00:00").recommendedAtDisplay())
        assertEquals("not-a-date", sampleEntry(recommendedAt = "not-a-date").recommendedAtDisplay())
        assertEquals("", sampleEntry(recommendedAt = "  ").recommendedAtDisplay())
        assertEquals("", sampleEntry(recommendedAt = null).recommendedAtDisplay())
    }

    @Test
    fun alternativesMapToUiModelsWithFallbacks() {
        val products = sampleEntry(
            alternatives = listOf(
                RecommendationHistoryAlternative(
                    productName = "  ",
                    brand = null,
                    matchReason = "",
                ),
                RecommendationHistoryAlternative(
                    productName = "Rice Drink",
                    brand = "Alt",
                    matchReason = "Same category",
                ),
            ),
        ).toAlternativeProducts()

        assertEquals("Alternative product", products[0].name)
        assertEquals("", products[0].brand)
        assertEquals("Same category alternative", products[0].description)
        assertEquals("Rice Drink", products[1].name)
        assertEquals("Alt", products[1].brand)
        assertEquals("Same category", products[1].description)
    }

    @Test
    fun safeVerdictParsesAndBlankSourceKeepsNullFields() {
        val entry = sampleEntry(sourceVerdict = "SAFE")
        assertEquals(ScanVerdict.SAFE, entry.verdict())
        assertEquals(null, sampleEntry().sourceProduct().productName)
    }

    private fun sampleEntry(
        sourceProductName: String? = null,
        sourceBrand: String? = null,
        sourceBarcode: String? = null,
        sourceVerdict: String? = "SAFE",
        recommendedAt: String? = "2026-08-01T10:00:00",
        alternatives: List<RecommendationHistoryAlternative> = emptyList(),
    ): RecommendationHistoryEntry {
        return RecommendationHistoryEntry(
            scanId = 9L,
            sourceBarcode = sourceBarcode,
            sourceProductName = sourceProductName,
            sourceBrand = sourceBrand,
            sourceVerdict = sourceVerdict,
            recommendedAt = recommendedAt,
            alternatives = alternatives,
        )
    }
}
