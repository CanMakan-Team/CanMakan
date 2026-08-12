package sg.edu.nus.iss.canmakan.features.product.recommendation.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryResponse

class ServerRecommendationHistoryRepositoryTest {
    @Test
    fun positiveProfileIdIsForwarded() = runTest {
        val api = FakeRecommendationHistoryApiService()

        val result = ServerRecommendationHistoryRepository(api)
            .getRecommendationHistoryForProfile(77L)

        assertEquals(77L, api.lastProfileId)
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun nonpositiveProfileIdIsRejectedBeforeNetworkRequest() {
        val api = FakeRecommendationHistoryApiService()

        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                ServerRecommendationHistoryRepository(api)
                    .getRecommendationHistoryForProfile(0L)
            }
        }

        assertEquals(null, api.lastProfileId)
    }

    private class FakeRecommendationHistoryApiService : RecommendationHistoryApiService {
        var lastProfileId: Long? = null

        override suspend fun getRecommendationHistory(
            profileId: Long,
        ): RecommendationHistoryResponse {
            lastProfileId = profileId
            return RecommendationHistoryResponse(profileId = profileId)
        }
    }
}
