package sg.edu.nus.iss.canmakan.features.product.model

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
        fun fromHistoryEntry(
            entry: ScanHistoryEntry,
            alternatives: List<AlternativeProduct> = emptyList()
        ): VerdictDetail {
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
                flags = flags,
                alternatives = alternatives
            )
        }
    }
}
