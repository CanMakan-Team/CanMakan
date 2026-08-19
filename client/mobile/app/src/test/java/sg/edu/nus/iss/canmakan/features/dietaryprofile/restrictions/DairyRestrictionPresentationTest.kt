package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction

class DairyRestrictionPresentationTest {

    @Test
    fun catalogHidesLactoseAliasesAndRenamesDairy() {
        val presented = DairyRestrictionPresentation.presentCatalog(
            listOf(
                restriction(1L, "DAIRY", "Dairy"),
                restriction(2L, "LACTOSE_INTOLERANT", "Lactose"),
                restriction(3L, "HALAL", "Halal"),
            ),
        )
        assertEquals(listOf("DAIRY", "HALAL"), presented.map { it.code })
        assertEquals("Lactose Intolerance", presented.first { it.code == "DAIRY" }.displayName)
        assertEquals("Halal", presented.first { it.code == "HALAL" }.displayName)
    }

    @Test
    fun dairyFreeAlsoUsesMergedDisplayName() {
        val presented = DairyRestrictionPresentation.presentCatalog(
            listOf(restriction(4L, "dairy_free", "Dairy Free")),
        )
        assertEquals("Lactose Intolerance", presented.single().displayName)
    }

    @Test
    fun aliasOnlySelectionSurfacesDairyAndDropsAliasIds() {
        val catalog = listOf(
            restriction(10L, "DAIRY", "Dairy"),
            restriction(11L, "LACTOSE", "Lactose"),
        )
        val presented = DairyRestrictionPresentation.presentSelections(
            catalog,
            mapOf(11L to "INTOLERANCE"),
        )
        assertEquals(mapOf(10L to "INTOLERANCE"), presented)
    }

    @Test
    fun saveDropsHiddenAliasIds() {
        val catalog = listOf(
            restriction(10L, "DAIRY", "Dairy"),
            restriction(11L, "LACTOSE_INTOLERANT", "Lactose"),
        )
        val saved = DairyRestrictionPresentation.selectionsForSave(
            catalog,
            mapOf(10L to "STRICT_AVOID", 11L to "INTOLERANCE"),
        )
        assertEquals(mapOf(10L to "STRICT_AVOID"), saved)
    }

    private fun restriction(id: Long, code: String, displayName: String): DietaryRestriction {
        return DietaryRestriction(id, code, displayName, "ALLERGEN")
    }
}
