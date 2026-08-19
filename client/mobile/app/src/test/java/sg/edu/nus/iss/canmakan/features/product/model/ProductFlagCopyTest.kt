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
        assertEquals("Rule", flags[0].category)
        assertEquals("Dairy", flags[0].label)
        assertEquals("Allergen", flags[1].category)
        assertEquals("Milk", flags[1].label)
        assertEquals("Summary", flags.last().category)
    }

    @Test
    fun glutenAllergyHistoryUsesRuleHeader() {
        val flags = ProductFlagCopy.flagsFromHistoryFindings(
            matchedRules = listOf("GLUTEN_ALLERGY"),
            allergensFound = listOf("Wheat Flour"),
            summary = "Contains wheat flour which violates the gluten-free constraint.",
        )

        assertEquals("Rule", flags[0].category)
        assertEquals("Gluten Allergy", flags[0].label)
        assertEquals("Allergen", flags[1].category)
        assertEquals("Wheat Flour", flags[1].label)
    }

    @Test
    fun groupsMultipleRulesAndAllergensIntoSingleCards() {
        val flags = ProductFlagCopy.flagsFromHistoryFindings(
            matchedRules = listOf("GLUTEN_ALLERGY", "DAIRY_INTOLERANCE"),
            allergensFound = listOf("Wheat Flour", "Milk Solids", "Whey"),
            summary = null,
        )

        assertEquals(2, flags.size)
        assertEquals("Rule", flags[0].category)
        assertEquals("Gluten Allergy, Dairy Intolerance", flags[0].label)
        assertEquals("Allergen", flags[1].category)
        assertEquals("Wheat Flour, Milk Solids, Whey", flags[1].label)
    }

    @Test
    fun liveFindingsGroupRulesAndAllergens() {
        val flags = ProductFlagCopy.flagsFromFindings(
            findings = listOf(
                Triple("DAIRY", "Milk", "Contains milk"),
                Triple("PEANUT", "Peanuts", "Contains peanuts"),
            ),
        )

        assertEquals(2, flags.size)
        assertEquals("Rule", flags[0].category)
        assertEquals("Contains milk, Contains peanuts", flags[0].label)
        assertEquals("Allergen", flags[1].category)
        assertEquals("Milk, Peanuts", flags[1].label)
    }

    @Test
    fun flagsFromFindingsUsesSummaryWhenNoFindings() {
        assertEquals(
            listOf(ProductFlag("Summary", "Check the label.")),
            ProductFlagCopy.flagsFromFindings(emptyList(), "Check the label."),
        )
        assertTrue(ProductFlagCopy.flagsFromFindings(emptyList(), "  ").isEmpty())
    }

    @Test
    fun titlesAndBodiesCoverDataQualityAndBlankCodes() {
        assertEquals("Info", ProductFlagCopy.titleForCode(null))
        assertEquals("Info", ProductFlagCopy.titleForCode(" "))
        assertEquals("Unverified ingredient", ProductFlagCopy.titleForCode("UNRESOLVED"))
        assertEquals("Possible cross-contamination", ProductFlagCopy.titleForCode("CROSS_CONTAMINATION"))
        assertEquals("Summary", ProductFlagCopy.titleForCode("SUMMARY"))
        assertEquals("Info", ProductFlagCopy.titleForCode("INFO"))
        assertEquals("Allergen", ProductFlagCopy.titleForCode("ALLERGEN"))
        assertEquals("Rule", ProductFlagCopy.titleForCode("DAIRY"))
        assertEquals("Custom Code", ProductFlagCopy.humanizeCode("CUSTOM_CODE"))
        assertTrue(ProductFlagCopy.isSubjectSentinel(" LABEL "))
        assertTrue(ProductFlagCopy.isSubjectSentinel(null))
        assertEquals(
            "Some ingredients could not be verified against this dietary profile.",
            ProductFlagCopy.defaultBodyForCode("UNRESOLVED"),
        )
        assertEquals(
            "The label mentions possible traces that may affect this profile.",
            ProductFlagCopy.defaultBodyForCode("CROSS_CONTAMINATION"),
        )
    }

    @Test
    fun flagFromFindingFallsBackToHumanizedRuleName() {
        val flag = ProductFlagCopy.flagFromFinding(
            restrictionCode = "  ",
            ingredientName = "unknown",
            reason = null,
        )
        assertEquals("Info", flag.category)
        assertEquals("Flagged by dietary rules", flag.label)
    }

    @Test
    fun groupRuleAndAllergenFlagsSkipsBlankCards() {
        val grouped = ProductFlagCopy.groupRuleAndAllergenFlags(
            listOf(
                ProductFlag("Rule", "Dairy"),
                ProductFlag("Rule", "Gluten"),
                ProductFlag(" ", "ignored"),
                ProductFlag("Summary", null),
                ProductFlag("Incomplete product data", "Check the label."),
            ),
        )
        assertEquals("Rule", grouped[0].category)
        assertEquals("Dairy, Gluten", grouped[0].label)
        assertEquals("Incomplete product data", grouped[1].category)
    }

    @Test
    fun historyUsesDefaultUnresolvedBodyAndSkipsBlankRules() {
        val flags = ProductFlagCopy.flagsFromHistoryFindings(
            matchedRules = listOf(" ", "UNRESOLVED"),
            allergensFound = listOf("nutrition"),
            summary = "Distinct summary.",
        )
        assertEquals("Unverified ingredient", flags[0].category)
        assertEquals("Summary", flags.last().category)
    }
}
