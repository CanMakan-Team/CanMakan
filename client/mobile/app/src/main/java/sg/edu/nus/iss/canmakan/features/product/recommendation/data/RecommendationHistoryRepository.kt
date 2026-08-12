package sg.edu.nus.iss.canmakan.features.product.recommendation.data

import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryEntry

interface RecommendationHistoryRepository {
    suspend fun getRecommendationHistoryForProfile(profileId: Long): List<RecommendationHistoryEntry>
}
