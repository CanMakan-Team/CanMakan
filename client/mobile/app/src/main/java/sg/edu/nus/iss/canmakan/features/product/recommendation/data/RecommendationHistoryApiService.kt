package sg.edu.nus.iss.canmakan.features.product.recommendation.data

import retrofit2.http.GET
import retrofit2.http.Path
import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryResponse

interface RecommendationHistoryApiService {
    @GET("profiles/{profileId}/recommendation-history")
    suspend fun getRecommendationHistory(
        @Path("profileId") profileId: Long
    ): RecommendationHistoryResponse
}
