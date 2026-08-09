package sg.edu.nus.iss.canmakan.features.family.ui

import sg.edu.nus.iss.canmakan.features.family.data.FamilyRestrictionSumRes

/**
 * (UC6) Create a Sealed Interface to Represent
 * the Distinct States of the Restriction Summary Screen.
 */
sealed interface FamilyRestrictionSummaryUiState {
    object Loading : FamilyRestrictionSummaryUiState
    data class Success (
        val data: FamilyRestrictionSumRes,
        val uniqueRestrictions: List<String>
    ) : FamilyRestrictionSummaryUiState
    object Empty : FamilyRestrictionSummaryUiState
    data class Error(val message: String) : FamilyRestrictionSummaryUiState
}