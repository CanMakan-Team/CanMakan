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
            // History only stores rule codes + ingredient names (not Finding.reason),
            // so map codes into plain-language titles and default bodies.
            val flags = ProductFlagCopy.flagsFromHistoryFindings(
                matchedRules = entry.findingsJson.matchedRules,
                allergensFound = entry.findingsJson.allergensFound,
                summary = entry.aiExplanation,
            )

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
