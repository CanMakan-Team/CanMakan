package sg.edu.nus.iss.canmakan.features.product.history

import java.io.IOException
import java.time.LocalDateTime
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
import sg.edu.nus.iss.canmakan.features.product.history.data.ScanHistoryRepository
import sg.edu.nus.iss.canmakan.features.product.model.FindingsJson
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ScanHistoryEntry
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.features.product.recommendation.data.RecommendationHistoryRepository
import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryAlternative
import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryEntry

class ScanHistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var activeProfileManager: ActiveProfileManager
    private lateinit var scanHistoryRepository: FakeScanHistoryRepository
    private lateinit var recommendationHistoryRepository: FakeRecommendationHistoryRepository
    private lateinit var viewModel: ScanHistoryViewModel

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        activeProfileManager = ActiveProfileManager()
        scanHistoryRepository = FakeScanHistoryRepository()
        recommendationHistoryRepository = FakeRecommendationHistoryRepository()
        viewModel = ScanHistoryViewModel(
            activeProfileManager,
            scanHistoryRepository,
            recommendationHistoryRepository
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("UC4 V1: Loads scan history for the active profile")
    fun loadsScanHistoryForActiveProfile() = runTest {
        val entry = sampleEntry(profileId = 2L)
        scanHistoryRepository.entriesByProfile = mapOf(2L to listOf(entry))

        activeProfileManager.switchProfile(2L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.scanHistoryUiState.value
        assertEquals(listOf(entry), uiState.scanHistory)
        assertEquals(2L, scanHistoryRepository.lastRequestedProfileId)
    }

    @Test
    @DisplayName("UC4 V2: Shows a loading state while scan history loads")
    fun showsLoadingStateWhileHistoryLoads() = runTest {
        scanHistoryRepository.gate = CompletableDeferred()

        activeProfileManager.switchProfile(3L)
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.scanHistoryUiState.value.isLoading)

        scanHistoryRepository.gate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.scanHistoryUiState.value.isLoading)
    }

    @Test
    @DisplayName("UC4 V3: Shows an empty state when the profile has no scan history")
    fun showsEmptyStateWhenNoScanHistory() = runTest {
        scanHistoryRepository.entriesByProfile = mapOf(4L to emptyList())

        activeProfileManager.switchProfile(4L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.scanHistoryUiState.value
        assertTrue(uiState.scanHistory.isEmpty())
        assertFalse(uiState.isLoading)
        assertNull(uiState.errorMessage)
    }

    @Test
    @DisplayName("UC4 V4: Shows an error state when scan history fails to load")
    fun showsErrorStateWhenHistoryLoadFails() = runTest {
        scanHistoryRepository.shouldThrow = true

        activeProfileManager.switchProfile(5L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.scanHistoryUiState.value
        assertNotNull(uiState.errorMessage)
        assertFalse(uiState.isLoading)
        assertTrue(uiState.scanHistory.isEmpty())
    }

    @Test
    @DisplayName("UC17 V1: Builds alternatives map keyed by scan id")
    fun buildsAlternativesMapFromRecommendationHistory() = runTest {
        val entry = sampleEntry(profileId = 2L, scanId = 7L)
        scanHistoryRepository.entriesByProfile = mapOf(2L to listOf(entry))
        recommendationHistoryRepository.entriesByProfile = mapOf(
            2L to listOf(
                RecommendationHistoryEntry(
                    scanId = 7L,
                    sourceBarcode = entry.barcode,
                    sourceProductName = entry.product.productName,
                    sourceBrand = entry.product.brand,
                    sourceVerdict = "UNSAFE",
                    recommendedAt = "2026-01-05T09:00:00",
                    alternatives = listOf(
                        RecommendationHistoryAlternative(
                            barcode = "7394376618253",
                            productName = "Oatly Barista",
                            brand = "Oatly",
                            matchReason = "Dairy-free substitute",
                            rankScore = 0.92,
                            discoveryTier = "SAME_CATEGORY"
                        )
                    )
                )
            )
        )

        activeProfileManager.switchProfile(2L)
        testDispatcher.scheduler.advanceUntilIdle()

        val alternatives = viewModel.scanHistoryUiState.value.alternativesByScanId[7L].orEmpty()
        assertEquals(1, alternatives.size)
        assertEquals("Oatly Barista", alternatives[0].name)
    }

    @Test
    @DisplayName("UC17 V2: Scan history still loads when recommendation history fails")
    fun scanHistoryLoadsWhenRecommendationHistoryFails() = runTest {
        val entry = sampleEntry(profileId = 6L)
        scanHistoryRepository.entriesByProfile = mapOf(6L to listOf(entry))
        recommendationHistoryRepository.shouldThrow = true

        activeProfileManager.switchProfile(6L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.scanHistoryUiState.value
        assertEquals(listOf(entry), uiState.scanHistory)
        assertTrue(uiState.alternativesByScanId.isEmpty())
        assertNull(uiState.errorMessage)
    }

    private fun sampleEntry(profileId: Long, scanId: Long = 1L) = ScanHistoryEntry(
        id = scanId,
        profileId = profileId,
        barcode = "95500539",
        product = Product("Sardines in tomato sauce", "Ayam Brand", "95500539"),
        scannedAt = LocalDateTime.of(2026, 1, 5, 9, 0, 0),
        verdict = ScanVerdict.SAFE,
        findingsJson = FindingsJson(),
        aiExplanation = "This product contains no gluten ingredients or wheat derivatives.",
    )

    private class FakeScanHistoryRepository : ScanHistoryRepository {
        var entriesByProfile: Map<Long, List<ScanHistoryEntry>> = emptyMap()
        var lastRequestedProfileId: Long? = null
        var gate: CompletableDeferred<Unit>? = null
        var shouldThrow = false

        override suspend fun getScanHistoryForProfile(profileId: Long): List<ScanHistoryEntry> {
            gate?.await()
            lastRequestedProfileId = profileId
            if (shouldThrow) throw IOException("network down")
            return entriesByProfile[profileId] ?: emptyList()
        }
    }

    private class FakeRecommendationHistoryRepository : RecommendationHistoryRepository {
        var entriesByProfile: Map<Long, List<RecommendationHistoryEntry>> = emptyMap()
        var shouldThrow = false

        override suspend fun getRecommendationHistoryForProfile(
            profileId: Long
        ): List<RecommendationHistoryEntry> {
            if (shouldThrow) throw IOException("network down")
            return entriesByProfile[profileId] ?: emptyList()
        }
    }
}
