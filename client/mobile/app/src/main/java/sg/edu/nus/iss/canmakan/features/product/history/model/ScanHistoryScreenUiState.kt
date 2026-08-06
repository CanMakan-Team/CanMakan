package sg.edu.nus.iss.canmakan.features.product.history.model

import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.product.model.ScanHistoryEntry

data class ScanHistoryScreenUiState (
    val scanHistory: List<ScanHistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)