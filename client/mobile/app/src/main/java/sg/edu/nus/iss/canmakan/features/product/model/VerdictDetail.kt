package sg.edu.nus.iss.canmakan.features.product.model

import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryEntry

/* In-memory payload passed from scan/history into the product detail screen.
 *
 * author Amelia
 */
data class VerdictDetail(
    val product: Product,
    val verdict: ScanVerdict,
    val explanation: String? = null,
    val flags: List<ProductFlag> = emptyList(),
    val alternatives: List<AlternativeProduct> = emptyList(),
    val alternativesError: String? = null
) {
    companion object {
        fun fromHistoryEntry(entry: ScanHistoryEntry): VerdictDetail {

            // Add flags per tier and for summary
            val flags = buildList {
                entry.findingsJson.matchedRules.forEach { 
                    rule -> add(ProductFlag("RULE", rule))
                }
                entry.findingsJson.allergensFound.forEach { 
                    allergen -> add(ProductFlag("ALLERGEN", allergen))
                }
                entry.aiExplanation
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(ProductFlag("SUMMARY", it)) }
            }

            return VerdictDetail(
                product = entry.product,
                verdict = entry.verdict,
                explanation = entry.aiExplanation,
                flags = flags
            )
        }

        fun fromRecommendationHistoryEntry(entry: RecommendationHistoryEntry): VerdictDetail {
            return VerdictDetail(
                product = entry.sourceProduct(),
                verdict = entry.verdict(),
                alternatives = entry.toAlternativeProducts()
            )
        }
    }
}
