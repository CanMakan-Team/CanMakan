package sg.edu.nus.iss.canmakan.features.product.recommendation.data

import jakarta.inject.Inject
import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryEntry

class ServerRecommendationHistoryRepository @Inject constructor(
    private val apiService: RecommendationHistoryApiService
) : RecommendationHistoryRepository {
    override suspend fun getRecommendationHistoryForProfile(
        profileId: Long
    ): List<RecommendationHistoryEntry> {
        require(profileId > 0) { "Recommendation history profile id must be positive." }
        return apiService.getRecommendationHistory(profileId).history
    }
}
