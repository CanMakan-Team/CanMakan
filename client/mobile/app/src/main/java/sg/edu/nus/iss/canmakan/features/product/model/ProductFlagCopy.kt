package sg.edu.nus.iss.canmakan.features.product.model

import java.util.Locale

/**
 * Maps engine restriction codes and subject sentinels into plain-language
 * flag titles and body text for the product detail Flags tab.
 */
object ProductFlagCopy {

    /** Backend Finding subject sentinels — not real allergen names. */
    private val subjectSentinels = setOf("unknown", "label", "nutrition")

    fun isSubjectSentinel(name: String?): Boolean {
        val trimmed = name?.trim().orEmpty()
        return trimmed.isEmpty() || trimmed.lowercase(Locale.US) in subjectSentinels
    }

    /**
     * Short card header for a restriction / data-quality code.
     * Example: INCOMPLETE_DATA → "Incomplete product data"
     */
    fun titleForCode(code: String?): String {
        val normalized = code?.trim().orEmpty()
        if (normalized.isEmpty()) return "Info"
        return when (normalized.uppercase(Locale.US)) {
            "INCOMPLETE_DATA" -> "Incomplete product data"
            "UNRESOLVED" -> "Unverified ingredient"
            "CROSS_CONTAMINATION" -> "Possible cross-contamination"
            "SUMMARY" -> "Summary"
            "INFO" -> "Info"
            "RULE" -> "Dietary rule"
            "ALLERGEN" -> "Allergen"
            else -> humanizeCode(normalized)
        }
    }

    /**
     * Fallback body when the API did not supply a plain-language reason
     * (common on the history path, which only keeps matched rule codes).
     */
    fun defaultBodyForCode(code: String?): String? {
        return when (code?.trim()?.uppercase(Locale.US)) {
            "INCOMPLETE_DATA" ->
                "No reliable ingredient data for this product — please check the physical label."
            "UNRESOLVED" ->
                "Some ingredients could not be verified against this dietary profile."
            "CROSS_CONTAMINATION" ->
                "The label mentions possible traces that may affect this profile."
            else -> null
        }
    }

    fun humanizeCode(code: String): String {
        return code
            .trim()
            .split('_', ' ', '-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.US).replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                }
            }
            .ifBlank { code }
    }

    fun flagFromFinding(
        restrictionCode: String?,
        ingredientName: String?,
        reason: String?,
    ): ProductFlag {
        val code = restrictionCode?.takeIf { it.isNotBlank() } ?: "INFO"
        val body = reason?.takeIf { it.isNotBlank() }
            ?: defaultBodyForCode(code)
            ?: listOfNotNull(
                ingredientName?.takeUnless { isSubjectSentinel(it) },
            ).joinToString(" · ")
                .ifBlank { "Flagged by dietary rules" }

        return ProductFlag(
            category = titleForCode(code),
            label = body,
        )
    }

    fun flagsFromHistoryFindings(
        matchedRules: List<String>,
        allergensFound: List<String>,
        summary: String?,
    ): List<ProductFlag> = buildList {
        val summaryText = summary?.takeIf { it.isNotBlank() }
        // Prefer the stored explanation as the incomplete-data body so we do not
        // also render an identical Summary card.
        var summaryUsedAsFindingBody = false

        matchedRules.forEach { rule ->
            val code = rule.trim()
            if (code.isEmpty()) return@forEach
            val body = if (
                !summaryUsedAsFindingBody &&
                code.equals("INCOMPLETE_DATA", ignoreCase = true) &&
                summaryText != null
            ) {
                summaryUsedAsFindingBody = true
                summaryText
            } else {
                defaultBodyForCode(code) ?: humanizeCode(code)
            }
            add(
                ProductFlag(
                    category = titleForCode(code),
                    label = body,
                )
            )
        }
        allergensFound.forEach { allergen ->
            if (isSubjectSentinel(allergen)) return@forEach
            add(
                ProductFlag(
                    category = "Allergen",
                    label = humanizeCode(allergen),
                )
            )
        }
        if (summaryText != null &&
            !summaryUsedAsFindingBody &&
            none { flag -> textsRoughlyEqual(flag.label, summaryText) }
        ) {
            add(ProductFlag(category = "Summary", label = summaryText))
        }
    }

    /** Compare flag/summary copy ignoring punctuation and spacing differences. */
    fun textsRoughlyEqual(a: String?, b: String?): Boolean {
        val left = normalizeForCompare(a)
        val right = normalizeForCompare(b)
        return left.isNotEmpty() && left == right
    }

    private fun normalizeForCompare(value: String?): String {
        return value
            .orEmpty()
            .lowercase(Locale.US)
            .replace(Regex("[\\u2013\\u2014\\-]"), " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
