package sg.edu.nus.iss.canmakan.features.product.recommendation.model

import sg.edu.nus.iss.canmakan.features.product.model.AlternativeProduct
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.shared.util.BACKEND_LOCAL_DATE_TIME_FORMATTER
import sg.edu.nus.iss.canmakan.shared.util.toScanHistoryDisplayString
import java.time.LocalDateTime

data class RecommendationHistoryResponse(
    val profileId: Long,
    val history: List<RecommendationHistoryEntry> = emptyList()
)

data class RecommendationHistoryEntry(
    val scanId: Long?,
    val sourceBarcode: String? = null,
    val sourceProductName: String? = null,
    val sourceBrand: String? = null,
    val sourceVerdict: String?,
    val recommendedAt: String?,
    val alternatives: List<RecommendationHistoryAlternative> = emptyList()
) {
    fun sourceProduct(): Product = Product(
        productName = sourceProductName,
        brand = sourceBrand,
        barcode = sourceBarcode,
    )

    fun verdict(): ScanVerdict = sourceVerdict
        ?.trim()
        ?.uppercase()
        ?.let { runCatching { ScanVerdict.valueOf(it) }.getOrNull() }
        ?: ScanVerdict.WARNING

    fun recommendedAtDisplay(): String = recommendedAt
        ?.takeIf { it.isNotBlank() }
        ?.let { timestamp ->
            runCatching {
                LocalDateTime.parse(timestamp, BACKEND_LOCAL_DATE_TIME_FORMATTER)
                    .toScanHistoryDisplayString()
            }.getOrDefault(timestamp)
        }
        .orEmpty()

    fun toAlternativeProducts(): List<AlternativeProduct> = alternatives.map { it.toUiModel() }
}

data class RecommendationHistoryAlternative(
    val barcode: String? = null,
    val productName: String? = null,
    val brand: String? = null,
    val matchReason: String? = null,
    val rankScore: Double? = null,
    val discoveryTier: String? = null,
) {
    fun toUiModel() = AlternativeProduct(
        name = productName?.takeIf { it.isNotBlank() } ?: "Alternative product",
        brand = brand.orEmpty(),
        description = matchReason?.takeIf { it.isNotBlank() } ?: "Same category alternative",
    )
}
