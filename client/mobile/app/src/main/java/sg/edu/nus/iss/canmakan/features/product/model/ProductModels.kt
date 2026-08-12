package sg.edu.nus.iss.canmakan.features.product.model

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime
import sg.edu.nus.iss.canmakan.shared.network.AlternativeProductDto

// Represents a single food product that has been scanned or is being reviewed.
// Fields are nullable because Gson can deserialize JSON null into them at runtime.
data class Product(
    val productName: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
) {
    val displayName: String
        get() = productName?.takeIf { it.isNotBlank() } ?: "Unknown product"

    val displayBrand: String
        get() = brand.orEmpty()

    val displayBarcode: String
        get() = barcode.orEmpty()
}

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
    val scannedAt: LocalDateTime,
    val verdict: ScanVerdict,
    val findingsJson: FindingsJson,
    val aiExplanation: String? = null
)

// A single flagged reason shown on the product detail screen,
// such as an allergen or a dietary conflict.
data class ProductFlag(
    val category: String? = null,
    val label: String? = null,
)

// A suggested replacement product shown on the Alternatives tab.
data class AlternativeProduct(
    val name: String,
    val brand: String,
    val description: String
)

fun AlternativeProductDto.toUiModel() = AlternativeProduct(
    name = productName?.takeIf { it.isNotBlank() } ?: "Alternative product",
    brand = brand.orEmpty(),
    description = matchReason?.takeIf { it.isNotBlank() } ?: "Same category alternative",
)
