package sg.edu.nus.iss.canmakan.features.product.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ProductFlagCopyTest {

    @Test
    fun incompleteDataHistoryGetsTitleAndExplanationBody() {
        val flags = ProductFlagCopy.flagsFromHistoryFindings(
            matchedRules = listOf("INCOMPLETE_DATA"),
            allergensFound = listOf("unknown"),
            summary = null,
        )

        assertEquals(1, flags.size)
        assertEquals("Incomplete product data", flags[0].category)
        assertTrue(flags[0].label!!.contains("ingredient data", ignoreCase = true))
    }

    @Test
    fun fromHistoryEntrySkipsUnknownAllergenSentinel() {
        val detail = VerdictDetail.fromHistoryEntry(
            ScanHistoryEntry(
                id = 1L,
                profileId = 1L,
                barcode = "123",
                product = Product("Test", "Brand", "123"),
                scannedAt = LocalDateTime.of(2026, 1, 1, 12, 0),
                verdict = ScanVerdict.WARNING,
                findingsJson = FindingsJson(
                    matchedRules = listOf("INCOMPLETE_DATA"),
                    allergensFound = listOf("unknown"),
                ),
                aiExplanation = null,
            )
        )

        assertEquals(1, detail.flags.size)
        assertEquals("Incomplete product data", detail.flags[0].category)
    }

    @Test
    fun liveFindingUsesReasonAsBody() {
        val flag = ProductFlagCopy.flagFromFinding(
            restrictionCode = "INCOMPLETE_DATA",
            ingredientName = "unknown",
            reason = "No reliable ingredient data for this product - please verify the physical label.",
        )

        assertEquals("Incomplete product data", flag.category)
        assertTrue(flag.label!!.contains("physical label", ignoreCase = true))
    }

    @Test
    fun skipsSummaryWhenItDuplicatesIncompleteDataBody() {
        val explanation =
            "No reliable ingredient data for this product - please verify the physical label."
        val flags = ProductFlagCopy.flagsFromHistoryFindings(
            matchedRules = listOf("INCOMPLETE_DATA"),
            allergensFound = listOf("unknown"),
            summary = explanation,
        )

        assertEquals(1, flags.size)
        assertEquals("Incomplete product data", flags[0].category)
        assertEquals(explanation, flags[0].label)
    }

    @Test
    fun keepsDistinctSummaryAlongsideFindings() {
        val flags = ProductFlagCopy.flagsFromHistoryFindings(
            matchedRules = listOf("DAIRY"),
            allergensFound = listOf("Milk"),
            summary = "Contains dairy ingredients that conflict with this profile.",
        )

        assertEquals(3, flags.size)
        assertEquals("Summary", flags.last().category)
    }
}
