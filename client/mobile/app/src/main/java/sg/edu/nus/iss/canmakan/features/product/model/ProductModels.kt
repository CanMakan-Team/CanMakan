package sg.edu.nus.iss.canmakan.features.product.model

// Represents a single food product that has been scanned or is being reviewed.
data class Product(
    val name: String,
    val brand: String,
    val barcode: String
)

// The three possible outcomes after checking a product against a dietary profile.
enum class ScanStatus {
    SAFE,
    WARNING,
    AVOID
}

// One row in the scan history list.
data class ScanHistoryEntry(
    val product: Product,
    val date: String,
    val status: ScanStatus,
    val note: String? = null
)

// A single flagged reason shown on the product detail screen,
// such as an allergen or a dietary conflict.
data class ProductFlag(
    val category: String,
    val label: String
)

// A suggested replacement product shown on the Alternatives tab.
data class AlternativeProduct(
    val name: String,
    val brand: String,
    val description: String
)
