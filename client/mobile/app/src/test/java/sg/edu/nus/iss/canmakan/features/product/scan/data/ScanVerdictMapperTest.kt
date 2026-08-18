package sg.edu.nus.iss.canmakan.features.product.scan.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.shared.network.AlternativeProductDto
import sg.edu.nus.iss.canmakan.shared.network.AssessmentResponse
import sg.edu.nus.iss.canmakan.shared.network.RecommendationResponse

class ScanVerdictMapperTest {

    @Test
    fun parseVerdictAcceptsTrimmedCaseInsensitiveNames() {
        assertEquals(ScanVerdict.SAFE, ScanVerdictMapper.parseVerdict(" safe "))
        assertEquals(ScanVerdict.UNSAFE, ScanVerdictMapper.parseVerdict("unsafe"))
        assertNull(ScanVerdictMapper.parseVerdict("maybe"))
        assertNull(ScanVerdictMapper.parseVerdict(" "))
    }

    @Test
    fun toVerdictDetailFallsBackWhenProductNameAndBarcodeAreBlank() {
        val detail = ScanVerdictMapper.toVerdictDetail(
            response = AssessmentResponse(
                verdict = "SAFE",
                explanation = "ok",
                productName = "  ",
                barcode = "",
            ),
            verdict = ScanVerdict.SAFE,
            fallbackBarcode = "999",
            alternatives = emptyList(),
            alternativesError = null,
        )

        assertEquals("Unknown product", detail.product.productName)
        assertEquals("999", detail.product.barcode)
    }

    @Test
    fun alternativesFromMapsRecommendationRows() {
        val alternatives = ScanVerdictMapper.alternativesFrom(
            RecommendationResponse(
                sourceBarcode = "111",
                alternatives = listOf(
                    AlternativeProductDto(
                        barcode = "222",
                        productName = "Fine Salt",
                        brand = "Morton",
                        matchReason = "category_match",
                    ),
                ),
            ),
        )

        assertEquals(1, alternatives.size)
        assertEquals("Fine Salt", alternatives[0].name)
        assertEquals("Morton", alternatives[0].brand)
    }
}
