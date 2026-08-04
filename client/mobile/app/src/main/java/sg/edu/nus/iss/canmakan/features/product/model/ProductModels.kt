package sg.edu.nus.iss.canmakan.features.product.model

import com.google.gson.annotations.SerializedName

// Represents a single food product that has been scanned or is being reviewed.
data class Product(
    val productName: String,
    val brand: String,
    val barcode: String
)

// The three possible outcomes after checking a product against a dietary profile.
enum class ScanVerdict {
    SAFE,
    WARNING,
    UNSAFE
}

data class FindingsJson (
    @SerializedName("matched_rules")
    val matchedRules: List<String> = emptyList(),
    @SerializedName("allergens_found")
    val allergensFound: List<String> = emptyList()
)

// One row in the scan history list.
data class ScanHistoryEntry(
    val id: Long,
    val profileId: Long,
    val barcode: String,
    val product: Product,
    val scannedAt: String,
    val verdict: ScanVerdict,
    val findingsJson: FindingsJson,
    val aiExplanation: String? = null
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
