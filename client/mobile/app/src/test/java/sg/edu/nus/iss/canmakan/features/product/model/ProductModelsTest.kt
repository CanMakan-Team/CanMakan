package sg.edu.nus.iss.canmakan.features.product.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.shared.network.AlternativeProductDto

class ProductModelsTest {

    @Test
    fun displayFieldsFallBackWhenNameBrandOrBarcodeMissing() {
        val blank = Product("  ", null, null)
        assertEquals("Unknown product", blank.displayName)
        assertEquals("", blank.displayBrand)
        assertEquals("", blank.displayBarcode)

        val named = Product("Oat Drink", "Brand", "123")
        assertEquals("Oat Drink", named.displayName)
        assertEquals("Brand", named.displayBrand)
        assertEquals("123", named.displayBarcode)
    }

    @Test
    fun alternativeDtoMapsNameBrandAndReasonWithFallbacks() {
        val fallback = AlternativeProductDto().toUiModel()
        assertEquals("Alternative product", fallback.name)
        assertEquals("", fallback.brand)
        assertEquals("Same category alternative", fallback.description)

        val named = AlternativeProductDto(
            productName = "Rice Drink",
            brand = "Alt",
            matchReason = "Same category",
        ).toUiModel()
        assertEquals("Rice Drink", named.name)
        assertEquals("Alt", named.brand)
        assertEquals("Same category", named.description)
    }
}
