package sg.edu.nus.iss.canmakan.features.product.model

import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import java.time.LocalDateTime

// Sample data used to fill the screens while there is no backend or
// database connected yet. In a later version this would come from a
// server or a local database instead.
object ProductSampleData {

    val scanHistory = listOf(
        ScanHistoryEntry(
            product = Product("Lay's Classic Chips", "Frito-Lay", "0028400047685"),
            id = 1L,
            profileId = 1L,
            barcode = "0028400047685",
            scannedAt = LocalDateTime.of(2026, 7, 24, 9, 15),
            verdict = ScanVerdict.SAFE,
            findingsJson = FindingsJson()
        ),
        ScanHistoryEntry(
            id = 2L,
            profileId = 1L,
            product = Product("Nutella Hazelnut Spread", "Ferrero", "8000500037165"),
            barcode = "8000500037165",
            scannedAt = LocalDateTime.of(2026, 7, 24, 8, 40),
            verdict = ScanVerdict.UNSAFE,
            findingsJson = FindingsJson(
                matchedRules = listOf("Peanuts", "Non-Halal E471")
            )
        ),
        ScanHistoryEntry(
            id = 3L,
            profileId = 1L,
            product = Product("Chobani Greek Yogurt", "Chobani", "0894700010152"),
            barcode = "0894700010152",
            scannedAt = LocalDateTime.of(2026, 7, 23, 18, 5),
            verdict = ScanVerdict.WARNING,
            findingsJson = FindingsJson(
                allergensFound = listOf("Dairy")
            )
        ),
//        ScanHistoryEntry(
//            product = Product("Lundberg Rice Cakes", "Lundberg", "0073416009306"),
//            date = "Jul 23",
//            status = ScanVerdict.SAFE
//        ),
//        ScanHistoryEntry(
//            product = Product("Morning Granola Bar", "Nature's Best", "0011110849104"),
//            date = "Jul 22",
//            status = ScanVerdict.WARNING,
//            note = "Egg traces"
//        ),
//        ScanHistoryEntry(
//            product = Product("Tom Yum Paste", "Kitchen Leaf", "8850124003216"),
//            date = "Jul 22",
//            status = ScanVerdict.UNSAFE
//        )
    )

//    val profiles = listOf(
//        DietaryProfile(id = 1L,"Sarah", "Parent", "ME"),
//        DietaryProfile(id = 2L,"Alice", "Child · Age 9", "AL"),
//        DietaryProfile(id = 3L,"Ben", "Child · Age 13", "BN"),
//        DietaryProfile(id = 4L,"Grandma", "Dependant", "GM")
//    )

    // The product shown on the product detail screen, used for both the
    // "Flags & Details" tab and the "Alternatives" tab.
    val scannedProduct = Product("Ferrero Rocher", "Ferrero", "8000500037165")

    val productFlags = listOf(
        ProductFlag("ALLERGEN", "Hazelnuts (Tree Nuts)"),
        ProductFlag("DIETARY", "Non-Halal Emulsifier (E471)")
    )

    val alternatives = listOf(
        AlternativeProduct(
            "Lily's Dark Chocolate",
            "Lily's Sweets",
            "Halal certified · nut-free · low sugar"
        ),
        AlternativeProduct(
            "Enjoy Life Choco Chips",
            "Enjoy Life Foods",
            "Halal · free from 14 major allergens"
        )
    )

//    fun religiousOptions() = listOf(
//        DietaryRestriction("Halal", isSelected = true),
//        DietaryOption("Kosher")
//    )
//
//    fun allergyOptions() = listOf(
//        DietaryOption("Dairy-Free"),
//        DietaryOption("Egg Allergy"),
//        DietaryOption("Gluten Allergy"),
//        DietaryOption("Lactose Intolerance"),
//        DietaryOption("Peanut Allergy"),
//        DietaryOption("Sesame Allergy"),
//        DietaryOption("Tree Nut Allergy")
//    )
//
//    fun specificDietOptions() = listOf(
//        DietaryOption("Keto Diet"),
//        DietaryOption("Low Cholesterol"),
//        DietaryOption("Low Salt")
//    )
}
