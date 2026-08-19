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
     * Matched dietary rules use "Rule" (same pattern as "Allergen");
     * data-quality codes keep a specific title.
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
            "RULE" -> "Rule"
            "ALLERGEN" -> "Allergen"
            else -> if (isDataQualityCode(normalized)) {
                humanizeCode(normalized)
            } else {
                "Rule"
            }
        }
    }

    private fun isDataQualityCode(code: String): Boolean {
        return when (code.trim().uppercase(Locale.US)) {
            "INCOMPLETE_DATA", "UNRESOLVED", "CROSS_CONTAMINATION" -> true
            else -> false
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
                // Fall back to a readable rule name when history-like codes have no reason.
                humanizeCode(code).takeUnless {
                    it.equals("Info", ignoreCase = true) || isDataQualityCode(code)
                },
            ).joinToString(" · ")
                .ifBlank { "Flagged by dietary rules" }

        return ProductFlag(
            category = titleForCode(code),
            label = body,
        )
    }

    /**
     * Builds Flags-tab cards from a live assessment finding list.
     * Rule and Allergen entries are each collapsed into a single card.
     */
    fun flagsFromFindings(
        findings: List<Triple<String?, String?, String?>>,
        summaryFallback: String? = null,
    ): List<ProductFlag> {
        val ruleOrDataFlags = mutableListOf<ProductFlag>()
        val allergenLabels = linkedSetOf<String>()

        findings.forEach { (restrictionCode, ingredientName, reason) ->
            ruleOrDataFlags.add(flagFromFinding(restrictionCode, ingredientName, reason))
            if (!isSubjectSentinel(ingredientName) &&
                !isDataQualityCode(restrictionCode.orEmpty())
            ) {
                allergenLabels.add(humanizeCode(ingredientName!!.trim()))
            }
        }

        val grouped = groupRuleAndAllergenFlags(ruleOrDataFlags).toMutableList()
        if (allergenLabels.isNotEmpty() && grouped.none { it.category.equals("Allergen", ignoreCase = true) }) {
            // Insert Allergen after Rule / data-quality cards, before Summary if any.
            val summaryIndex = grouped.indexOfFirst { it.category.equals("Summary", ignoreCase = true) }
            val allergenFlag = ProductFlag("Allergen", allergenLabels.joinToString(", "))
            if (summaryIndex >= 0) {
                grouped.add(summaryIndex, allergenFlag)
            } else {
                grouped.add(allergenFlag)
            }
        }

        if (grouped.isNotEmpty()) return grouped
        return summaryFallback
            ?.takeIf { it.isNotBlank() }
            ?.let { listOf(ProductFlag(titleForCode("SUMMARY"), it)) }
            ?: emptyList()
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

        val ruleLabels = linkedSetOf<String>()
        matchedRules.forEach { rule ->
            val code = rule.trim()
            if (code.isEmpty()) return@forEach
            if (isDataQualityCode(code)) {
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
                add(ProductFlag(category = titleForCode(code), label = body))
            } else {
                ruleLabels.add(humanizeCode(code))
            }
        }
        if (ruleLabels.isNotEmpty()) {
            add(ProductFlag(category = "Rule", label = ruleLabels.joinToString(", ")))
        }

        val allergenLabels = linkedSetOf<String>()
        allergensFound.forEach { allergen ->
            if (isSubjectSentinel(allergen)) return@forEach
            allergenLabels.add(humanizeCode(allergen))
        }
        if (allergenLabels.isNotEmpty()) {
            add(ProductFlag(category = "Allergen", label = allergenLabels.joinToString(", ")))
        }

        if (summaryText != null &&
            !summaryUsedAsFindingBody &&
            none { flag -> textsRoughlyEqual(flag.label, summaryText) }
        ) {
            add(ProductFlag(category = "Summary", label = summaryText))
        }
    }

    /**
     * Collapses repeated Rule / Allergen cards into one card each, joining labels.
     * Other categories (Incomplete product data, Summary, …) stay separate.
     */
    fun groupRuleAndAllergenFlags(flags: List<ProductFlag>): List<ProductFlag> {
        val mergeCategories = setOf("Rule", "Allergen")
        val mergedLabels = linkedMapOf<String, LinkedHashSet<String>>()
        flags.forEach { flag ->
            val category = flag.category?.takeIf { it.isNotBlank() } ?: return@forEach
            val label = flag.label?.takeIf { it.isNotBlank() } ?: return@forEach
            if (category in mergeCategories) {
                mergedLabels.getOrPut(category) { linkedSetOf() }.add(label)
            }
        }

        val emitted = mutableSetOf<String>()
        return buildList {
            flags.forEach { flag ->
                val category = flag.category?.takeIf { it.isNotBlank() } ?: return@forEach
                if (category in mergeCategories) {
                    if (category !in emitted) {
                        emitted.add(category)
                        val labels = mergedLabels[category].orEmpty()
                        if (labels.isNotEmpty()) {
                            add(ProductFlag(category = category, label = labels.joinToString(", ")))
                        }
                    }
                } else if (!flag.label.isNullOrBlank()) {
                    add(flag)
                }
            }
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
