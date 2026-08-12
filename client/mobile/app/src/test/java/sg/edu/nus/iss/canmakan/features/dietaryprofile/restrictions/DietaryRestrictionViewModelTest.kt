package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Response
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.family.data.ActiveProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.ClaimInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateDependantProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateFamilyRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.CreateInvitationRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.DependantProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMemberRosterItem
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileApiService
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileResponse
import sg.edu.nus.iss.canmakan.features.family.data.FamilyRestrictionSumRes
import sg.edu.nus.iss.canmakan.features.family.data.InvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationResponse
import sg.edu.nus.iss.canmakan.features.family.data.SetActiveProfileRequestBody
import sg.edu.nus.iss.canmakan.features.family.data.UserSearchResponse
import sg.edu.nus.iss.canmakan.testing.signInTestUser
import sg.edu.nus.iss.canmakan.testing.testAuthSessionStore

/* Mobile tests for updating an app user's dietary profile. */
@OptIn(ExperimentalCoroutinesApi::class)
class DietaryRestrictionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var activeProfileManager: ActiveProfileManager
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var repository: FakeDietaryRestrictionRepository
    private lateinit var familyApi: FakeFamilyProfileApiService
    private lateinit var viewModel: DietaryRestrictionViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        activeProfileManager = ActiveProfileManager()
        sessionStore = testAuthSessionStore().also { it.signInTestUser() }
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 1L)
        repository = FakeDietaryRestrictionRepository()
        familyApi = FakeFamilyProfileApiService()
        viewModel = DietaryRestrictionViewModel(
            activeProfileManager,
            repository,
            sessionStore,
            FamilyProfileRepository(familyApi),
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("UC1 M1: Loads dietary restrictions for the active profile")
    fun loadsDietaryRestrictionsForActiveProfile() = runTest {
        repository.savedSelections = mapOf(1L to "STRICT_AVOID")

        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 999L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(2, uiState.religiousRestrictions.size)
        assertEquals(2, uiState.allergenRestrictions.size)
        assertEquals(1, uiState.dietRestrictions.size)
        assertTrue(uiState.selectedRestrictions.containsKey(1L))
        assertEquals(true, uiState.allowRestrictionEdit)
    }

    @Test
    @DisplayName("UC1 M2: Allows only one religious restriction to be selected")
    fun allowsOnlyOneReligiousRestrictionSelection() {
        assertEquals(2, viewModel.uiState.value.religiousRestrictions.size)

        viewModel.selectReligiousRestriction(10L)
        viewModel.selectReligiousRestriction(11L)

        val selected = viewModel.uiState.value.selectedRestrictions
        assertEquals(1, selected.size)
        assertTrue(selected.containsKey(11L))
        assertEquals("STRICT_AVOID", selected[11L])
    }

    @Test
    @DisplayName("UC1 M3: Toggles multiple dietary restrictions for non-religious categories")
    fun togglesMultipleDietaryRestrictions() {
        viewModel.toggleDietaryRestriction(20L)
        viewModel.toggleDietaryRestriction(21L)

        val selected = viewModel.uiState.value.selectedRestrictions
        assertEquals(2, selected.size)
        assertTrue(selected.containsKey(20L))
        assertTrue(selected.containsKey(21L))
    }

    @Test
    @DisplayName("UC1 M4: Saves current selections and reports success")
    fun savesCurrentSelectionsAndReportsSuccess() = runTest {
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 42L)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleDietaryRestriction(20L)
        viewModel.toggleDietaryRestriction(21L)

        var saved = false
        viewModel.onSave { saved = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(saved)
        assertEquals(
            mapOf(20L to "STRICT_AVOID", 21L to "STRICT_AVOID"),
            repository.lastSavedSelections,
        )
        assertEquals(42L, repository.lastSavedProfileId)
    }

    @Test
    @DisplayName("UC1 M5: Shows a loading state while the catalog loads")
    fun showsLoadingStateWhileCatalogLoads() = runTest {
        repository.gate = CompletableDeferred()

        val job = launch { viewModel.loadDietaryRestrictions() }
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        repository.gate?.complete(Unit)
        job.join()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    @DisplayName("UC1 M6: Shows an empty state when the catalog is empty")
    fun showsEmptyStateWhenCatalogIsEmpty() = runTest {
        repository.catalog = emptyList()

        viewModel.loadDietaryRestrictions()

        val uiState = viewModel.uiState.value
        assertTrue(uiState.religiousRestrictions.isEmpty())
        assertTrue(uiState.allergenRestrictions.isEmpty())
        assertTrue(uiState.dietRestrictions.isEmpty())
        assertFalse(uiState.isLoading)
        assertNull(uiState.errorMessage)
    }

    @Test
    @DisplayName("UC1 M7: Shows an error state when the catalog fails to load")
    fun showsErrorStateWhenCatalogLoadFails() = runTest {
        val failingRepository = FakeDietaryRestrictionRepository().apply { loadShouldThrow = true }
        val freshSessionStore = testAuthSessionStore().also { it.signInTestUser() }
        val freshManager = ActiveProfileManager().also {
            it.switchProfile(requireNotNull(freshSessionStore.accountKey.value), 1L)
        }
        val freshViewModel = DietaryRestrictionViewModel(
            freshManager,
            failingRepository,
            freshSessionStore,
            FamilyProfileRepository(FakeFamilyProfileApiService()),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = freshViewModel.uiState.value
        assertNotNull(uiState.errorMessage)
        assertFalse(uiState.isLoading)
        assertTrue(uiState.religiousRestrictions.isEmpty())
        assertEquals(false, uiState.allowRestrictionEdit)
    }

    @Test
    @DisplayName("UC1 M8: Shows an error state when saving fails")
    fun showsErrorStateWhenSaveFails() = runTest {
        repository.saveShouldSucceed = false
        viewModel.toggleDietaryRestriction(20L)

        var succeeded = false
        viewModel.onSave { succeeded = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(succeeded)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun profilelessSaveIsBlockedBeforeRepositoryCall() = runTest {
        activeProfileManager.reset()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleDietaryRestriction(20L)

        viewModel.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.saveCalls)
        assertTrue(viewModel.uiState.value.errorMessage?.contains("profile setup") == true)
        assertEquals(false, viewModel.uiState.value.allowRestrictionEdit)
    }

    @Test
    fun accountSwitchClearsSelectionsImmediatelyAndIgnoresStaleLoad() = runTest {
        repository.savedSelectionsByProfile[42L] = mapOf(10L to "STRICT_AVOID")
        repository.blockedLoadProfileId = 42L
        repository.loadGate = CompletableDeferred()
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 42L)
        testDispatcher.scheduler.runCurrent()

        repository.savedSelectionsByProfile[84L] = mapOf(21L to "STRICT_AVOID")
        sessionStore.signInTestUser(22L, "other@example.com")
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 84L)
        testDispatcher.scheduler.runCurrent()

        assertEquals(mapOf(21L to "STRICT_AVOID"), viewModel.uiState.value.selectedRestrictions)
        assertFalse(viewModel.uiState.value.selectedRestrictions.containsKey(10L))

        repository.loadGate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf(21L to "STRICT_AVOID"), viewModel.uiState.value.selectedRestrictions)
        assertEquals(true, viewModel.uiState.value.allowRestrictionEdit)
    }

    @Test
    fun staleSaveCannotCallSuccessOrMutateNewAccountsState() = runTest {
        viewModel.toggleDietaryRestriction(20L)
        repository.blockedSaveProfileId = 1L
        repository.saveGate = CompletableDeferred()
        var successCalled = false
        viewModel.onSave { successCalled = true }
        testDispatcher.scheduler.runCurrent()

        repository.savedSelectionsByProfile[84L] = mapOf(21L to "STRICT_AVOID")
        sessionStore.signInTestUser(22L, "other@example.com")
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 84L)
        testDispatcher.scheduler.runCurrent()

        repository.saveGate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(successCalled)
        assertEquals(mapOf(21L to "STRICT_AVOID"), viewModel.uiState.value.selectedRestrictions)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun oldSessionResultIsIgnoredAfterAccountCyclesBackToSameUserId() = runTest {
        repository.savedSelectionsByProfile[42L] = mapOf(10L to "STRICT_AVOID")
        repository.blockedLoadProfileId = 42L
        repository.loadGate = CompletableDeferred()
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 42L)
        testDispatcher.scheduler.runCurrent()

        sessionStore.signInTestUser(22L, "other@example.com")
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 84L)
        testDispatcher.scheduler.runCurrent()

        repository.savedSelectionsByProfile[42L] = mapOf(21L to "STRICT_AVOID")
        sessionStore.signInTestUser(TEST_USER_ID, "person@example.com")
        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 42L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(mapOf(21L to "STRICT_AVOID"), viewModel.uiState.value.selectedRestrictions)

        repository.loadGate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf(21L to "STRICT_AVOID"), viewModel.uiState.value.selectedRestrictions)
    }

    @Test
    fun oldLoadIsIgnoredAfterSameProfileIsSelectedAgain() = runTest {
        repository.savedSelectionsByProfile[42L] = mapOf(10L to "STRICT_AVOID")
        repository.blockedLoadProfileId = 42L
        repository.loadGate = CompletableDeferred()
        val accountKey = requireNotNull(sessionStore.accountKey.value)
        activeProfileManager.switchProfile(accountKey, 42L)
        testDispatcher.scheduler.runCurrent()

        repository.savedSelectionsByProfile[84L] = emptyMap()
        activeProfileManager.switchProfile(accountKey, 84L)
        testDispatcher.scheduler.runCurrent()

        repository.savedSelectionsByProfile[42L] = mapOf(21L to "STRICT_AVOID")
        activeProfileManager.switchProfile(accountKey, 42L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(mapOf(21L to "STRICT_AVOID"), viewModel.uiState.value.selectedRestrictions)

        repository.loadGate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf(21L to "STRICT_AVOID"), viewModel.uiState.value.selectedRestrictions)
    }

    @Test
    @DisplayName("UC1 M9: Locks the sheet for a non-admin editing another adult's profile (D3)")
    fun locksSheetForOtherAdultLinkedProfile() = runTest {
        familyApi.meResponse = Response.success(
            FamilyMeResponse(
                familyId = 50L,
                familyName = "Wong Family",
                memberRole = "MEMBER",
                selfProfileId = 77L,
                createdByUserId = TEST_USER_ID,
            ),
        )
        familyApi.membersResponse = Response.success(
            listOf(
                FamilyMemberRosterItem(
                    memberId = TEST_USER_ID,
                    profileId = 77L,
                    linkedUserId = TEST_USER_ID,
                    profileName = "Wong",
                    relationship = "SELF",
                    source = "REGISTERED_USER",
                ),
                FamilyMemberRosterItem(
                    memberId = 22L,
                    profileId = 99L,
                    linkedUserId = 22L,
                    profileName = "Amanda",
                    relationship = "SPOUSE",
                    source = "REGISTERED_USER",
                ),
            ),
        )

        activeProfileManager.switchProfile(requireNotNull(sessionStore.accountKey.value), 99L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(false, uiState.allowRestrictionEdit)
        assertEquals(RestrictionEditAuthorization.READ_ONLY_HINT, uiState.restrictionEditHint)

        viewModel.toggleDietaryRestriction(20L)
        assertFalse(viewModel.uiState.value.selectedRestrictions.containsKey(20L))

        var saved = false
        viewModel.onSave { saved = true }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(saved)
        assertNull(repository.lastSavedSelections)
        assertEquals(0, repository.saveCalls)
    }

    private class FakeDietaryRestrictionRepository : DietaryRestrictionRepository {
        var savedSelections: Map<Long, String> = emptyMap()
        val savedSelectionsByProfile = mutableMapOf<Long, Map<Long, String>>()
        var lastSavedSelections: Map<Long, String>? = null
        var lastSavedProfileId: Long? = null
        var catalog: List<DietaryRestriction> = listOf(
            DietaryRestriction(10L, "HALAL", "Halal", "RELIGIOUS"),
            DietaryRestriction(11L, "VEGETARIAN", "Vegetarian", "RELIGIOUS"),
            DietaryRestriction(20L, "PEANUT", "Peanut Allergy", "ALLERGEN"),
            DietaryRestriction(21L, "MILK", "Milk Allergy", "ALLERGEN"),
            DietaryRestriction(30L, "LOW_CARB", "Low Carb", "DIET"),
        )
        var gate: CompletableDeferred<Unit>? = null
        var loadShouldThrow = false
        var saveShouldSucceed = true
        var saveCalls = 0
        var blockedLoadProfileId: Long? = null
        var loadGate: CompletableDeferred<Unit>? = null
        var blockedSaveProfileId: Long? = null
        var saveGate: CompletableDeferred<Unit>? = null

        override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> {
            gate?.await()
            if (loadShouldThrow) throw java.io.IOException("network down")
            return catalog
        }

        override suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String> {
            val result = savedSelectionsByProfile[profileId] ?: savedSelections
            if (profileId == blockedLoadProfileId) {
                blockedLoadProfileId = null
                withContext(NonCancellable) { loadGate?.await() }
            }
            return result
        }

        override suspend fun saveDietaryRestrictionSelections(
            profileId: Long,
            selections: Map<Long, String>,
        ): Boolean {
            saveCalls++
            lastSavedProfileId = profileId
            lastSavedSelections = selections
            if (profileId == blockedSaveProfileId) {
                withContext(NonCancellable) { saveGate?.await() }
            }
            return saveShouldSucceed
        }
    }

    private class FakeFamilyProfileApiService : FamilyProfileApiService {
        var meResponse: Response<FamilyMeResponse> = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        )
        var membersResponse: Response<List<FamilyMemberRosterItem>> = Response.success(emptyList())

        override suspend fun getMyFamily(): Response<FamilyMeResponse> = meResponse

        override suspend fun getFamilyMembers(): Response<List<FamilyMemberRosterItem>> = membersResponse

        override suspend fun createFamily(
            request: CreateFamilyRequestBody,
        ): Response<FamilyMeResponse> = errorResponse()

        override suspend fun getProfilesByFamilyId(familyId: Long): List<FamilyProfileResponse> =
            emptyList()

        override suspend fun getActiveProfile(): Response<ActiveProfileResponse> = errorResponse()

        override suspend fun setActiveProfile(
            request: SetActiveProfileRequestBody,
        ): Response<ActiveProfileResponse> = errorResponse()

        override suspend fun getFamilyRestrictionSummary(): Response<FamilyRestrictionSumRes> =
            errorResponse()

        override suspend fun searchUserByEmail(email: String): Response<UserSearchResponse> =
            errorResponse()

        override suspend fun createInvitation(
            request: CreateInvitationRequestBody,
        ): Response<InvitationResponse> = errorResponse()

        override suspend fun claimInvitation(
            request: ClaimInvitationRequestBody,
        ): Response<FamilyMeResponse> = errorResponse()

        override suspend fun listMyInvitations(): Response<List<PendingInvitationResponse>> =
            Response.success(emptyList())

        override suspend fun acceptInvitation(token: String): Response<FamilyMeResponse> =
            errorResponse()

        override suspend fun declineInvitation(token: String): Response<Unit> = errorResponse()

        override suspend fun createDependantProfile(
            request: CreateDependantProfileRequestBody,
        ): Response<DependantProfileResponse> = errorResponse()

        private fun <T> errorResponse(): Response<T> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
    }

    private companion object {
        const val TEST_USER_ID = 14L
    }
}
