package sg.edu.nus.iss.canmakan.features.product.history

import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.product.history.data.ScanHistoryRepository
import sg.edu.nus.iss.canmakan.features.product.model.FindingsJson
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ScanHistoryEntry
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.testing.signInTestUser
import sg.edu.nus.iss.canmakan.testing.testAuthSessionStore

/*
    Mobile ViewModel test for View Scan History, reached via the History icon
    in the bottom nav bar. Complements ServerScanHistoryRepositoryTest, which
    covers the repository layer; this file covers the loading/error/empty UI
    states ScanHistoryViewModel exposes to HistoryScreen (UC4-AC12, UC4-AC13).
 */
class ScanHistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var activeProfileManager: ActiveProfileManager
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var repository: FakeScanHistoryRepository
    private lateinit var viewModel: ScanHistoryViewModel

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        activeProfileManager = ActiveProfileManager()
        sessionStore = testAuthSessionStore().also { it.signInTestUser() }
        repository = FakeScanHistoryRepository()
        viewModel = ScanHistoryViewModel(activeProfileManager, repository, sessionStore)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // UC4-AC2/AC11 (profile scoping): loading history for the active profile
    // forwards that profile's id and surfaces its entries, not another
    // profile's.
    @Test
    @DisplayName("UC4 V1: Loads scan history for the active profile")
    fun loadsScanHistoryForActiveProfile() = runTest {
        val entry = sampleEntry(profileId = 2L)
        repository.entriesByProfile = mapOf(2L to listOf(entry))

        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 2L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.scanHistoryUiState.value
        assertEquals(listOf(entry), uiState.scanHistory)
        assertEquals(2L, repository.lastRequestedProfileId)
    }

    // UC4-AC13: loading state is shown while the history loads. switchProfile
    // itself is synchronous (just updates a StateFlow value); the ViewModel's
    // collector picks it up on testDispatcher, so runCurrent() alone advances
    // it far enough to observe the loading state before the gate is released.
    @Test
    @DisplayName("UC4 V2: Shows a loading state while scan history loads")
    fun showsLoadingStateWhileHistoryLoads() = runTest {
        repository.gate = CompletableDeferred()

        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 3L)
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.scanHistoryUiState.value.isLoading)

        repository.gate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.scanHistoryUiState.value.isLoading)
    }

    // UC4-AC12: an empty result is surfaced as an empty list, not an error,
    // so the screen can render its empty state rather than an error message.
    @Test
    @DisplayName("UC4 V3: Shows an empty state when the profile has no scan history")
    fun showsEmptyStateWhenNoScanHistory() = runTest {
        repository.entriesByProfile = mapOf(4L to emptyList())

        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 4L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.scanHistoryUiState.value
        assertTrue(uiState.scanHistory.isEmpty())
        assertFalse(uiState.isLoading)
        assertNull(uiState.errorMessage)
    }

    // UC4-AC13: a network failure surfaces as an error state, not a crash.
    @Test
    @DisplayName("UC4 V4: Shows an error state when scan history fails to load")
    fun showsErrorStateWhenHistoryLoadFails() = runTest {
        repository.shouldThrow = true

        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 5L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.scanHistoryUiState.value
        assertNotNull(uiState.errorMessage)
        assertFalse(uiState.isLoading)
        assertTrue(uiState.scanHistory.isEmpty())
    }

    @Test
    fun profilelessStateDoesNotRequestHistoryForZero() = runTest {
        activeProfileManager.reset()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(repository.lastRequestedProfileId)
        assertTrue(viewModel.scanHistoryUiState.value.errorMessage?.contains("profile setup") == true)
    }

    @Test
    fun accountSwitchLoadsOnlyNewOwnersHistoryAndIgnoresOldResult() = runTest {
        val oldEntry = sampleEntry(profileId = 2L)
        val newEntry = sampleEntry(profileId = 3L).copy(id = 2L, barcode = "new-account")
        repository.entriesByProfile = mapOf(2L to listOf(oldEntry), 3L to listOf(newEntry))
        repository.blockedProfileId = 2L
        repository.profileGate = CompletableDeferred()

        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 2L)
        testDispatcher.scheduler.runCurrent()

        sessionStore.signInTestUser(22L, "other@example.com")
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 3L)
        testDispatcher.scheduler.runCurrent()

        assertEquals(listOf(newEntry), viewModel.scanHistoryUiState.value.scanHistory)

        repository.profileGate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(newEntry), viewModel.scanHistoryUiState.value.scanHistory)
        assertEquals(3L, repository.lastRequestedProfileId)
    }

    private fun sampleEntry(profileId: Long) = ScanHistoryEntry(
        id = 1L,
        profileId = profileId,
        barcode = "95500539",
        product = Product("Sardines in tomato sauce", "Ayam Brand", "95500539"),
        scannedAt = LocalDateTime.of(2026, 1, 5, 9, 0, 0),
        verdict = ScanVerdict.SAFE,
        findingsJson = FindingsJson(),
        aiExplanation = "This product contains no gluten ingredients or wheat derivatives.",
    )

    // Fake repository for testing. Avoid using the real Retrofit-backed repo in tests.
    private class FakeScanHistoryRepository : ScanHistoryRepository {
        var entriesByProfile: Map<Long, List<ScanHistoryEntry>> = emptyMap()
        var lastRequestedProfileId: Long? = null
        var gate: CompletableDeferred<Unit>? = null
        var shouldThrow = false
        var blockedProfileId: Long? = null
        var profileGate: CompletableDeferred<Unit>? = null

        override suspend fun getScanHistoryForProfile(profileId: Long): List<ScanHistoryEntry> {
            val result = entriesByProfile[profileId] ?: emptyList()
            lastRequestedProfileId = profileId
            gate?.await()
            if (profileId == blockedProfileId) {
                withContext(NonCancellable) { profileGate?.await() }
            }
            if (shouldThrow) throw IOException("network down")
            return result
        }
    }

    private companion object {
        const val TEST_USER_ID = 14L
    }
}
