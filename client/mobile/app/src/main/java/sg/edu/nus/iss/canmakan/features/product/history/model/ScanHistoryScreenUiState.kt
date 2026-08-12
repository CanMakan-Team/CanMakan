package sg.edu.nus.iss.canmakan.features.product.history.model

import sg.edu.nus.iss.canmakan.features.product.model.AlternativeProduct
import sg.edu.nus.iss.canmakan.features.product.model.ScanHistoryEntry

data class ScanHistoryScreenUiState(
    val scanHistory: List<ScanHistoryEntry> = emptyList(),
    val alternativesByScanId: Map<Long, List<AlternativeProduct>> = emptyMap(),
    val isLoading: Boolean = false,
    val requiresProfileSetup: Boolean = false,
    val errorMessage: String? = null,
)
