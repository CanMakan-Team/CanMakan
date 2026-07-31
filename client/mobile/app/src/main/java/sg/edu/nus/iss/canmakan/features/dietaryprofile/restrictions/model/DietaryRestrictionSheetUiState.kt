package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model

data class DietaryRestrictionSheetUiState(
    val religiousRestrictions: List<DietaryRestriction> = emptyList(),
    val allergenRestrictions: List<DietaryRestriction> = emptyList(),
    val dietRestrictions: List<DietaryRestriction> = emptyList(),
    val selectedRestrictions: Map<Long, String> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
