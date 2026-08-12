package sg.edu.nus.iss.canmakan.features.product.recommendation

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.product.recommendation.data.RecommendationHistoryRepository
import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryAlternative
import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryEntry

class RecommendationHistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var activeProfileManager: ActiveProfileManager
    private lateinit var repository: FakeRecommendationHistoryRepository
    private lateinit var viewModel: RecommendationHistoryViewModel

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        activeProfileManager = ActiveProfileManager()
        repository = FakeRecommendationHistoryRepository()
        viewModel = RecommendationHistoryViewModel(activeProfileManager, repository)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("UC17 V1: Loads recommendation history for the active profile")
    fun loadsRecommendationHistoryForActiveProfile() = runTest {
        val entry = sampleEntry()
        repository.entriesByProfile = mapOf(2L to listOf(entry))

        activeProfileManager.switchProfile(2L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(listOf(entry), uiState.entries)
        assertEquals(2L, repository.lastRequestedProfileId)
    }

    @Test
    @DisplayName("UC17 V2: Shows a loading state while recommendation history loads")
    fun showsLoadingStateWhileHistoryLoads() = runTest {
        repository.gate = CompletableDeferred()

        activeProfileManager.switchProfile(3L)
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        repository.gate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    @DisplayName("UC17 V3: Shows an empty state when the profile has no recommendation history")
    fun showsEmptyStateWhenNoRecommendationHistory() = runTest {
        repository.entriesByProfile = mapOf(4L to emptyList())

        activeProfileManager.switchProfile(4L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertTrue(uiState.entries.isEmpty())
        assertFalse(uiState.isLoading)
        assertNull(uiState.errorMessage)
    }

    @Test
    @DisplayName("UC17 V4: Shows an error state when recommendation history fails to load")
    fun showsErrorStateWhenHistoryLoadFails() = runTest {
        repository.shouldThrow = true

        activeProfileManager.switchProfile(5L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNotNull(uiState.errorMessage)
        assertFalse(uiState.isLoading)
        assertTrue(uiState.entries.isEmpty())
    }

    private fun sampleEntry() = RecommendationHistoryEntry(
        scanId = 2L,
        sourceBarcode = "8888200602857",
        sourceProductName = "Farmhouse Fresh Milk",
        sourceBrand = "Farmhouse",
        sourceVerdict = "UNSAFE",
        recommendedAt = "2026-01-05T09:00:00",
        alternatives = listOf(
            RecommendationHistoryAlternative(
                barcode = "7394376618253",
                productName = "Oatly Barista",
                brand = "Oatly",
                matchReason = "Dairy-free oat milk substitute",
                rankScore = 0.92,
                discoveryTier = "SAME_CATEGORY"
            )
        )
    )

    private class FakeRecommendationHistoryRepository : RecommendationHistoryRepository {
        var entriesByProfile: Map<Long, List<RecommendationHistoryEntry>> = emptyMap()
        var lastRequestedProfileId: Long? = null
        var gate: CompletableDeferred<Unit>? = null
        var shouldThrow = false

        override suspend fun getRecommendationHistoryForProfile(
            profileId: Long
        ): List<RecommendationHistoryEntry> {
            gate?.await()
            lastRequestedProfileId = profileId
            if (shouldThrow) throw IOException("network down")
            return entriesByProfile[profileId] ?: emptyList()
        }
    }
}
