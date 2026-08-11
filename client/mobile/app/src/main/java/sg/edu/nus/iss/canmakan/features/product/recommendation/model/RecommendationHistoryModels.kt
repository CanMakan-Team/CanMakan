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
    val sourceBarcode: String,
    val sourceProductName: String,
    val sourceBrand: String,
    val sourceVerdict: String?,
    val recommendedAt: String?,
    val alternatives: List<RecommendationHistoryAlternative> = emptyList()
) {
    fun sourceProduct(): Product = Product(
        productName = sourceProductName,
        brand = sourceBrand,
        barcode = sourceBarcode
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
    val barcode: String,
    val productName: String,
    val brand: String,
    val matchReason: String?,
    val rankScore: Double?,
    val discoveryTier: String?
) {
    fun toUiModel() = AlternativeProduct(
        name = productName,
        brand = brand,
        description = matchReason ?: "Same category alternative"
    )
}
