package sg.edu.nus.iss.canmakan.features.product.scan

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Response
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.shared.network.AssessmentFinding
import sg.edu.nus.iss.canmakan.shared.network.AssessmentRequest
import sg.edu.nus.iss.canmakan.shared.network.AssessmentResponse
import sg.edu.nus.iss.canmakan.shared.network.CanMakanApiService
import sg.edu.nus.iss.canmakan.shared.network.ScanRequest
import sg.edu.nus.iss.canmakan.shared.network.ValidationResponse

/**
 * Mobile two-step scan flow: validate, then assess, then map the verdict.
 *
 * @author Amelia
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("UC3: ScannerViewModel validate then assess")
class ScannerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var api: FakeCanMakanApiService
    private lateinit var viewModel: ScannerViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        api = FakeCanMakanApiService()
        viewModel = ScannerViewModel(api)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("UC3 M1: valid food runs assess and lands on SUCCESS with verdict")
    fun validFoodRunsAssessAndSucceeds() = runTest {
        api.validation = Response.success(ValidationResponse(true, "food", "ok"))
        api.assessment = Response.success(
            AssessmentResponse(
                verdict = "SAFE",
                explanation = "No conflicts",
                findings = emptyList(),
                productName = "Nutella",
                barcode = "3017620422003"
            )
        )

        viewModel.processBarcode("3017620422003", profileId = 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScanProcessState.SUCCESS, viewModel.processState.value)
        assertEquals(ScanVerdict.SAFE, viewModel.verdictDetail.value?.verdict)
        assertEquals("Nutella", viewModel.verdictDetail.value?.product?.productName)
        assertTrue(api.assessCalled)
    }

    @Test
    @DisplayName("UC3 M2: invalid food stops before assess")
    fun invalidFoodStopsBeforeAssess() = runTest {
        api.validation = Response.success(
            ValidationResponse(false, "non-food", "Not a food product")
        )

        viewModel.processBarcode("123", profileId = 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScanProcessState.INVALID, viewModel.processState.value)
        assertEquals("Not a food product", viewModel.errorMessage.value)
        assertNull(viewModel.verdictDetail.value)
        assertTrue(!api.assessCalled)
    }

    @Test
    @DisplayName("UC3 M3: validate HTTP failure becomes ERROR and skips assess")
    fun validateHttpFailureBecomesError() = runTest {
        api.validation = Response.error(
            404,
            "not found".toResponseBody("application/json".toMediaType())
        )

        viewModel.processBarcode("999", profileId = 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScanProcessState.ERROR, viewModel.processState.value)
        assertEquals("Product not found or network error", viewModel.errorMessage.value)
        assertTrue(!api.assessCalled)
    }

    @Test
    @DisplayName("UC3 M4: assess HTTP failure becomes ERROR after validate succeeded")
    fun assessHttpFailureBecomesError() = runTest {
        api.validation = Response.success(ValidationResponse(true, "food", "ok"))
        api.assessment = Response.error(
            503,
            "down".toResponseBody("application/json".toMediaType())
        )

        viewModel.processBarcode("3017620422003", profileId = 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScanProcessState.ERROR, viewModel.processState.value)
        assertEquals("Could not generate a safety verdict", viewModel.errorMessage.value)
        assertTrue(api.assessCalled)
        assertNull(viewModel.verdictDetail.value)
    }

    @Test
    @DisplayName("UC3 M5: findings map into product flags")
    fun findingsMapIntoProductFlags() = runTest {
        api.validation = Response.success(ValidationResponse(true, "food", "ok"))
        api.assessment = Response.success(
            AssessmentResponse(
                verdict = "UNSAFE",
                explanation = "Contains dairy",
                findings = listOf(
                    AssessmentFinding("DAIRY", "Milk", "Contains milk")
                ),
                productName = "Yogurt",
                barcode = "111"
            )
        )

        viewModel.processBarcode("111", profileId = 1L)
        testDispatcher.scheduler.advanceUntilIdle()

        val detail = viewModel.verdictDetail.value!!
        assertEquals(ScanVerdict.UNSAFE, detail.verdict)
        assertEquals(1, detail.flags.size)
        assertEquals("DAIRY", detail.flags[0].category)
        assertEquals("Contains milk", detail.flags[0].label)
    }

    private class FakeCanMakanApiService : CanMakanApiService {
        var validation: Response<ValidationResponse> =
            Response.success(ValidationResponse(true, "food", "ok"))
        var assessment: Response<AssessmentResponse> =
            Response.success(AssessmentResponse("SAFE", "ok"))
        var assessCalled = false

        override suspend fun validateBarcode(request: ScanRequest): Response<ValidationResponse> {
            return validation
        }

        override suspend fun assessBarcode(
            request: AssessmentRequest
        ): Response<AssessmentResponse> {
            assessCalled = true
            return assessment
        }
    }
}
