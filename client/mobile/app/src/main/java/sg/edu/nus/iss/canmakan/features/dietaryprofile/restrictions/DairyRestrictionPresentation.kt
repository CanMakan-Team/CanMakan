package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction

/**
 * Client-only presentation of dairy-family catalog codes.
 * The backend catalog now has a single `DAIRY` row (the former separate
 * `LACTOSE_INTOLERANT` row was removed as a duplicate); this object is kept
 * defensive so any legacy/alternate code spelling still merges into one
 * selectable option and one summary row without changing the API.
 */
object DairyRestrictionPresentation {
    val HIDDEN_ALIAS_CODES = setOf("LACTOSE_INTOLERANT", "LACTOSE")
    private val PRIMARY_CODES = setOf("DAIRY", "DAIRY_FREE")
    const val MERGED_DISPLAY_NAME = "Lactose Intolerance"

    fun presentCatalog(restrictions: List<DietaryRestriction>): List<DietaryRestriction> =
        restrictions
            .filterNot { it.code.trim().uppercase() in HIDDEN_ALIAS_CODES }
            .map { restriction ->
                if (restriction.code.trim().uppercase() in PRIMARY_CODES) {
                    restriction.copy(displayName = MERGED_DISPLAY_NAME)
                } else {
                    restriction
                }
            }

    /**
     * If a profile only has a lactose alias selected, surface the primary DAIRY option as selected.
     * Alias ids are dropped from the UI selection map (they are not shown as chips).
     */
    fun presentSelections(
        catalog: List<DietaryRestriction>,
        saved: Map<Long, String>,
    ): Map<Long, String> {
        val aliasIds = catalog
            .filter { it.code.trim().uppercase() in HIDDEN_ALIAS_CODES }
            .map { it.id }
            .toSet()
        val dairyId = catalog
            .firstOrNull { it.code.trim().equals("DAIRY", ignoreCase = true) }
            ?.id

        val result = saved.toMutableMap()
        val aliasSeverity = aliasIds.firstNotNullOfOrNull { id -> saved[id] }
        aliasIds.forEach(result::remove)
        if (aliasSeverity != null && dairyId != null && dairyId !in result) {
            result[dairyId] = aliasSeverity
        }
        return result
    }

    /** Drop hidden lactose alias ids before persisting; keep the selected DAIRY id. */
    fun selectionsForSave(
        catalog: List<DietaryRestriction>,
        selections: Map<Long, String>,
    ): Map<Long, String> {
        val aliasIds = catalog
            .filter { it.code.trim().uppercase() in HIDDEN_ALIAS_CODES }
            .map { it.id }
            .toSet()
        return selections.filterKeys { it !in aliasIds }
    }
}
