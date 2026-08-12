package sg.edu.nus.iss.canmakan.features.product.recommendation.data

import jakarta.inject.Inject
import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryEntry

class ServerRecommendationHistoryRepository @Inject constructor(
    private val apiService: RecommendationHistoryApiService
) : RecommendationHistoryRepository {
    override suspend fun getRecommendationHistoryForProfile(
        profileId: Long
    ): List<RecommendationHistoryEntry> {
        return apiService.getRecommendationHistory(profileId).history
    }
}
