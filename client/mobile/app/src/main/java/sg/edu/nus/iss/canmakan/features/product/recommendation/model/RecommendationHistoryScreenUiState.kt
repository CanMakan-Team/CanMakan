package sg.edu.nus.iss.canmakan.features.product.recommendation.model

data class RecommendationHistoryScreenUiState(
    val entries: List<RecommendationHistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
