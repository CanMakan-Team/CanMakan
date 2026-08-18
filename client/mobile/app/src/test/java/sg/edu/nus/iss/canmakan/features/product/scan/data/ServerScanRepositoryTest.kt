package sg.edu.nus.iss.canmakan.features.product.scan.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.Response
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.shared.network.AssessmentRequest
import sg.edu.nus.iss.canmakan.shared.network.AssessmentResponse
import sg.edu.nus.iss.canmakan.shared.network.CanMakanApiService
import sg.edu.nus.iss.canmakan.shared.network.RecommendationResponse
import sg.edu.nus.iss.canmakan.shared.network.ScanFeedbackRequest
import sg.edu.nus.iss.canmakan.shared.network.ScanFeedbackResponse
import sg.edu.nus.iss.canmakan.shared.network.ScanRequest
import sg.edu.nus.iss.canmakan.shared.network.ValidationResponse

class ServerScanRepositoryTest {

    @Test
    fun validateBarcodeMapsHttpFailureAndInvalidFood() = runBlocking {
        val api = FakeApi()
        val repository = ServerScanRepository(api)

        api.validation = Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
        assertEquals(BarcodeValidation.Failed, repository.validateBarcode("1"))

        api.validation = Response.success(null)
        assertEquals(BarcodeValidation.Failed, repository.validateBarcode("1"))

        api.validation = Response.success(ValidationResponse(false, "non-food", "Not food"))
        assertEquals(BarcodeValidation.Invalid("Not food"), repository.validateBarcode("1"))

        api.validation = Response.success(ValidationResponse(true, "food", "ok"))
        assertEquals(BarcodeValidation.Valid, repository.validateBarcode("1"))
    }

    @Test
    fun assessBarcodeMapsFailedUnknownAndSuccess() = runBlocking {
        val api = FakeApi()
        val repository = ServerScanRepository(api)

        api.assessment = Response.error(503, "{}".toResponseBody("application/json".toMediaType()))
        assertEquals(ScanAssessment.Failed, repository.assessBarcode("1", 9L))

        api.assessment = Response.success(AssessmentResponse("MAYBE", "nope"))
        assertEquals(ScanAssessment.UnknownVerdict, repository.assessBarcode("1", 9L))

        api.assessment = Response.success(AssessmentResponse("SAFE", "ok"))
        val success = repository.assessBarcode("1", 9L) as ScanAssessment.Success
        assertEquals(ScanVerdict.SAFE, success.verdict)
    }

    @Test
    fun loadAlternativesUsesErrorCopyOnHttpFailureAndThrownException() = runBlocking {
        val api = FakeApi()
        val repository = ServerScanRepository(api)

        api.recommendations = Response.error(503, "{}".toResponseBody("application/json".toMediaType()))
        val failed = repository.loadAlternatives(1L, "111", 2L)
        assertTrue(failed.alternatives.isEmpty())
        assertEquals("Could not load alternatives", failed.errorMessage)

        api.throwOnRecommendations = true
        val thrown = repository.loadAlternatives(1L, "111", 2L)
        assertTrue(thrown.alternatives.isEmpty())
        assertEquals("Could not load alternatives", thrown.errorMessage)
    }

    @Test
    fun submitFeedbackTrimsBlankCommentsAndReturnsSuccessFlag() = runBlocking {
        val api = FakeApi()
        val repository = ServerScanRepository(api)

        assertTrue(repository.submitFeedback(7L, false, "  too salty  "))
        assertEquals("too salty", api.lastFeedback?.userComments)

        api.feedback = Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
        assertFalse(repository.submitFeedback(7L, true, "   "))
        assertNull(api.lastFeedback?.userComments)
    }

    private class FakeApi : CanMakanApiService {
        var validation: Response<ValidationResponse> =
            Response.success(ValidationResponse(true, "food", "ok"))
        var assessment: Response<AssessmentResponse> = Response.success(AssessmentResponse("SAFE", "ok"))
        var recommendations: Response<RecommendationResponse> =
            Response.success(RecommendationResponse("111", emptyList()))
        var feedback: Response<ScanFeedbackResponse> =
            Response.success(ScanFeedbackResponse(1L, 7L, false, null, false, null))
        var throwOnRecommendations = false
        var lastFeedback: ScanFeedbackRequest? = null

        override suspend fun validateBarcode(request: ScanRequest) = validation

        override suspend fun assessBarcode(request: AssessmentRequest) = assessment

        override suspend fun getRecommendations(
            profileId: Long,
            sourceBarcode: String,
            scanId: Long?,
        ): Response<RecommendationResponse> {
            if (throwOnRecommendations) throw IllegalStateException("offline")
            return recommendations
        }

        override suspend fun submitScanFeedback(
            scanId: Long,
            request: ScanFeedbackRequest,
        ): Response<ScanFeedbackResponse> {
            lastFeedback = request
            return feedback
        }
    }
}
