package sg.edu.nus.iss.canmakan.data

// Sample data used to fill the screens while there is no backend or
// database connected yet. In a later version this would come from a
// server or a local database instead.
object SampleData {

    val scanHistory = listOf(
        ScanHistoryEntry(
            product = Product("Lay's Classic Chips", "Frito-Lay", "0028400047685"),
            date = "Jul 24",
            status = ScanStatus.SAFE
        ),
        ScanHistoryEntry(
            product = Product("Nutella Hazelnut Spread", "Ferrero", "8000500037165"),
            date = "Jul 24",
            status = ScanStatus.AVOID,
            note = "Peanuts, Non-Halal E471"
        ),
        ScanHistoryEntry(
            product = Product("Chobani Greek Yogurt", "Chobani", "0894700010152"),
            date = "Jul 23",
            status = ScanStatus.WARNING,
            note = "Dairy"
        ),
        ScanHistoryEntry(
            product = Product("Lundberg Rice Cakes", "Lundberg", "0073416009306"),
            date = "Jul 23",
            status = ScanStatus.SAFE
        ),
        ScanHistoryEntry(
            product = Product("Morning Granola Bar", "Nature's Best", "0011110849104"),
            date = "Jul 22",
            status = ScanStatus.WARNING,
            note = "Egg traces"
        ),
        ScanHistoryEntry(
            product = Product("Tom Yum Paste", "Kitchen Leaf", "8850124003216"),
            date = "Jul 22",
            status = ScanStatus.AVOID
        )
    )

    val profiles = listOf(
        DietaryProfile("Sarah", "Parent", "ME"),
        DietaryProfile("Alice", "Child \u00B7 Age 9", "AL"),
        DietaryProfile("Ben", "Child \u00B7 Age 13", "BN"),
        DietaryProfile("Grandma", "Dependant", "GM")
    )

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
            "Halal certified \u00B7 nut-free \u00B7 low sugar"
        ),
        AlternativeProduct(
            "Enjoy Life Choco Chips",
            "Enjoy Life Foods",
            "Halal \u00B7 free from 14 major allergens"
        )
    )

    fun religiousOptions() = listOf(
        DietaryOption("Halal", isSelected = true),
        DietaryOption("Kosher")
    )

    fun allergyOptions() = listOf(
        DietaryOption("Dairy-Free"),
        DietaryOption("Egg Allergy"),
        DietaryOption("Gluten Allergy"),
        DietaryOption("Lactose Intolerant"),
        DietaryOption("Peanut Allergy"),
        DietaryOption("Sesame Allergy"),
        DietaryOption("Tree Nut Allergy")
    )

    fun specificDietOptions() = listOf(
        DietaryOption("Keto Diet"),
        DietaryOption("Low Cholesterol"),
        DietaryOption("Low Salt")
    )
}
