package sg.edu.nus.iss.canmakan.features.product.scan.data

import sg.edu.nus.iss.canmakan.features.product.model.AlternativeProduct
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ProductFlagCopy
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
import sg.edu.nus.iss.canmakan.features.product.model.toUiModel
import sg.edu.nus.iss.canmakan.shared.network.AssessmentResponse
import sg.edu.nus.iss.canmakan.shared.network.RecommendationResponse

object ScanVerdictMapper {
    fun parseVerdict(rawVerdict: String): ScanVerdict? {
        return runCatching {
            ScanVerdict.valueOf(rawVerdict.trim().uppercase())
        }.getOrNull()
    }

    fun toVerdictDetail(
        response: AssessmentResponse,
        verdict: ScanVerdict,
        fallbackBarcode: String,
        alternatives: List<AlternativeProduct>,
        alternativesError: String?,
    ): VerdictDetail {
        val flags = ProductFlagCopy.flagsFromFindings(
            findings = response.findings.map { finding ->
                Triple(finding.restrictionCode, finding.ingredientName, finding.reason)
            },
            summaryFallback = response.explanation,
        )
        return VerdictDetail(
            product = Product(
                productName = response.productName?.takeIf { it.isNotBlank() } ?: "Unknown product",
                brand = "",
                barcode = response.barcode?.takeIf { it.isNotBlank() } ?: fallbackBarcode,
            ),
            verdict = verdict,
            explanation = response.explanation,
            flags = flags,
            alternatives = alternatives,
            alternativesError = alternativesError,
            scanId = response.scanId,
        )
    }

    fun alternativesFrom(response: RecommendationResponse): List<AlternativeProduct> {
        return response.alternatives.map { it.toUiModel() }
    }
}
