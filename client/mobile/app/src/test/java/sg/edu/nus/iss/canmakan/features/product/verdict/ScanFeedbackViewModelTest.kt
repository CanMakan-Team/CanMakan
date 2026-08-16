package sg.edu.nus.iss.canmakan.features.product.verdict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Response
import sg.edu.nus.iss.canmakan.shared.network.CanMakanApiService
import sg.edu.nus.iss.canmakan.shared.network.AssessmentRequest
import sg.edu.nus.iss.canmakan.shared.network.AssessmentResponse
import sg.edu.nus.iss.canmakan.shared.network.RecommendationResponse
import sg.edu.nus.iss.canmakan.shared.network.ScanFeedbackRequest
import sg.edu.nus.iss.canmakan.shared.network.ScanFeedbackResponse
import sg.edu.nus.iss.canmakan.shared.network.ScanRequest
import sg.edu.nus.iss.canmakan.shared.network.ValidationResponse

/**
 * UC20: submitting thumbs up/down feedback on a scan verdict.
 *
 * @author Kwok Heng
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("UC20: ScanFeedbackViewModel feedback submission")
class ScanFeedbackViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var api: FakeCanMakanApiService
    private lateinit var viewModel: ScanFeedbackViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        api = FakeCanMakanApiService()
        viewModel = ScanFeedbackViewModel(api)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("UC20 M1: successful negative submission lands on SUBMITTED")
    fun successfulNegativeSubmissionLandsOnSubmitted() = runTest {
        api.feedback = Response.success(ScanFeedbackResponse(3L, 19L, false, "Wrong allergen", false, null))

        viewModel.submitNegativeFeedback(19L, "Wrong allergen")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(FeedbackSubmissionState.SUBMITTED, viewModel.submissionState.value)
        assertNull(viewModel.errorMessage.value)
        assertEquals(19L, api.lastScanId)
        assertEquals(false, api.lastRequest?.isPositive)
        assertEquals("Wrong allergen", api.lastRequest?.userComments)
    }

    @Test
    @DisplayName("UC20 M2: a blank comment is sent as null, not a blank string")
    fun blankCommentIsSentAsNull() = runTest {
        viewModel.submitNegativeFeedback(19L, "   ")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(api.lastRequest?.userComments)
    }

    @Test
    @DisplayName("UC20 M3: non-2xx response lands on ERROR with a message")
    fun failedResponseLandsOnError() = runTest {
        api.feedback = Response.error(
            404,
            "{\"message\":\"Scan was not found.\"}".toResponseBody("application/json".toMediaType())
        )

        viewModel.submitNegativeFeedback(19L, null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(FeedbackSubmissionState.ERROR, viewModel.submissionState.value)
        assertEquals("Could not submit feedback. Please try again.", viewModel.errorMessage.value)
    }

    @Test
    @DisplayName("UC20 M4: a network exception lands on ERROR instead of crashing")
    fun networkExceptionLandsOnError() = runTest {
        api.feedbackException = java.io.IOException("no connection")

        viewModel.submitNegativeFeedback(19L, null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(FeedbackSubmissionState.ERROR, viewModel.submissionState.value)
        assertEquals("no connection", viewModel.errorMessage.value)
    }

    @Test
    @DisplayName("UC20 M5: a thumbs up sends isPositive=true with no comment")
    fun positiveSubmissionSendsIsPositiveTrue() = runTest {
        viewModel.submitPositiveFeedback(19L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(19L, api.lastScanId)
        assertEquals(true, api.lastRequest?.isPositive)
        assertNull(api.lastRequest?.userComments)
    }

    @Test
    @DisplayName("UC20 M6: a thumbs up never touches the comment-form submission state")
    fun positiveSubmissionDoesNotTouchSubmissionState() = runTest {
        viewModel.submitPositiveFeedback(19L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(FeedbackSubmissionState.IDLE, viewModel.submissionState.value)
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    @DisplayName("UC20 M7: a thumbs up network failure is swallowed, not surfaced")
    fun positiveSubmissionFailureDoesNotSurfaceError() = runTest {
        api.feedbackException = java.io.IOException("no connection")

        viewModel.submitPositiveFeedback(19L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(FeedbackSubmissionState.IDLE, viewModel.submissionState.value)
        assertNull(viewModel.errorMessage.value)
    }

    private class FakeCanMakanApiService : CanMakanApiService {
        var feedback: Response<ScanFeedbackResponse> =
            Response.success(ScanFeedbackResponse(1L, 1L, false, null, false, null))
        var feedbackException: Exception? = null
        var lastScanId: Long? = null
        var lastRequest: ScanFeedbackRequest? = null

        override suspend fun validateBarcode(request: ScanRequest): Response<ValidationResponse> {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun assessBarcode(request: AssessmentRequest): Response<AssessmentResponse> {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun getRecommendations(
            profileId: Long,
            sourceBarcode: String,
            scanId: Long?
        ): Response<RecommendationResponse> {
            throw UnsupportedOperationException("not used by this test")
        }

        override suspend fun submitScanFeedback(
            scanId: Long,
            request: ScanFeedbackRequest
        ): Response<ScanFeedbackResponse> {
            lastScanId = scanId
            lastRequest = request
            feedbackException?.let { throw it }
            return feedback
        }
    }
}
