package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model

data class DietaryRestrictionSheetUiState(
    val religiousRestrictions: List<DietaryRestriction> = emptyList(),
    val allergenRestrictions: List<DietaryRestriction> = emptyList(),
    val dietRestrictions: List<DietaryRestriction> = emptyList(),
    val selectedRestrictions: Map<Long, String> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /**
     * D3 edit permission for the active profile.
     * null while permission is still being resolved (treat UI as view-only).
     */
    val allowRestrictionEdit: Boolean? = null,
    val restrictionEditHint: String? = null,
)
