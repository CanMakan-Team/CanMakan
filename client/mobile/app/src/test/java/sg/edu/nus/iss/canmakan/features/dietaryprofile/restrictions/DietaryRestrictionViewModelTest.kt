package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import retrofit2.Response
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

/*
    Mobile Test Cases for Use Case 1: Update App User Dietary Profile

    @author Amelia
 */
class DietaryRestrictionViewModelTest {

    // Test dispatcher: allows for asynchronous testing
    // Active profile mgr: allows for switching between profiles
    // Repository: allows for loading and saving dietary restrictions
    // View model: the object under test
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var activeProfileManager: ActiveProfileManager
    private lateinit var repository: FakeDietaryRestrictionRepository
    private lateinit var familyApi: FakeFamilyProfileApiService
    private lateinit var viewModel: DietaryRestrictionViewModel

    // Set up the test environment
    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        activeProfileManager = ActiveProfileManager()
        repository = FakeDietaryRestrictionRepository()
        familyApi = FakeFamilyProfileApiService()
        viewModel = DietaryRestrictionViewModel(
            activeProfileManager,
            repository,
            FamilyProfileRepository(familyApi),
        )
        // Resolve D3 permission (no family → editable) so toggle/save tests can run.
        activeProfileManager.switchProfile(1L)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    // Clean up the test environment
    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Testing the loading of dietary restrictions
    @Test
    @DisplayName("UC1 M1: Loads dietary restrictions for the active profile")
    fun loadsDietaryRestrictionsForActiveProfile() = runTest {
        repository.savedSelections = mapOf(1L to "STRICT_AVOID")

        // Trigger profile reload
        activeProfileManager.switchProfile(999L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(2, uiState.religiousRestrictions.size)
        assertEquals(2, uiState.allergenRestrictions.size)
        assertEquals(1, uiState.dietRestrictions.size)
        assertTrue(uiState.selectedRestrictions.containsKey(1L))
        assertTrue(uiState.allowRestrictionEdit == true)
    }

    // Testing the selection of dietary restrictions
    @Test
    @DisplayName("UC1 M2: Allows only one religious restriction to be selected")
    fun allowsOnlyOneReligiousRestrictionSelection() = runTest {
        // Catalog must be loaded so religious IDs are known for mutual exclusion.
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.religiousRestrictions.size)

        viewModel.selectReligiousRestriction(10L)
        viewModel.selectReligiousRestriction(11L)

        val selected = viewModel.uiState.value.selectedRestrictions
        assertEquals(1, selected.size)
        assertTrue(selected.containsKey(11L))
        assertEquals("STRICT_AVOID", selected[11L])
    }

    // Testing the toggling of multiple dietary restrictions
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

    // Testing the saving of dietary restrictions
    @Test
    @DisplayName("UC1 M4: Saves current selections and reports success")
    fun savesCurrentSelectionsAndReportsSuccess() = runTest {
        viewModel.toggleDietaryRestriction(20L)
        viewModel.toggleDietaryRestriction(21L)

        var saved = false
        viewModel.onSave { saved = true }

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(saved)
        assertEquals(mapOf(20L to "STRICT_AVOID", 21L to "STRICT_AVOID"), repository.lastSavedSelections)
    }

    // UC1-AC13: loading state while the catalog loads.
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

    // UC1-AC14: empty state when the catalog is empty, without crashing.
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

    // UC1-AC15: error state on a catalog load failure, without crashing. Uses a
    // fresh repository/ViewModel (rather than the shared one from setUp, which
    // has already completed one successful load) so this is a genuine first
    // load failing, not a reload over already-loaded data.
    @Test
    @DisplayName("UC1 M7: Shows an error state when the catalog fails to load")
    fun showsErrorStateWhenCatalogLoadFails() = runTest {
        val failingRepository = FakeDietaryRestrictionRepository().apply { loadShouldThrow = true }
        val freshViewModel = DietaryRestrictionViewModel(
            ActiveProfileManager(),
            failingRepository,
            FamilyProfileRepository(FakeFamilyProfileApiService()),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = freshViewModel.uiState.value
        assertNotNull(uiState.errorMessage)
        assertFalse(uiState.isLoading)
        assertTrue(uiState.religiousRestrictions.isEmpty())
    }

    // UC1-AC15: error state on a save failure, without crashing and without
    // reporting false success.
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
    @DisplayName("UC1 M9: PRIMARY_ADMIN may edit another adult's linked profile (D3)")
    fun allowsAdminToEditOtherAdultLinkedProfile() = runTest {
        familyApi.meResponse = Response.success(
            FamilyMeResponse(
                familyId = 50L,
                familyName = "Wong Family",
                memberRole = "PRIMARY_ADMIN",
                selfProfileId = 77L,
                createdByUserId = 14L,
            ),
        )

        activeProfileManager.switchProfile(99L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.allowRestrictionEdit == true)
        assertNull(viewModel.uiState.value.restrictionEditHint)

        viewModel.toggleDietaryRestriction(20L)
        assertTrue(viewModel.uiState.value.selectedRestrictions.containsKey(20L))
    }

    @Test
    @DisplayName("UC1 M10: Non-admin cannot edit another adult's linked profile (D3)")
    fun locksSheetForNonAdminOnOtherAdultLinkedProfile() = runTest {
        familyApi.meResponse = Response.success(
            FamilyMeResponse(
                familyId = 50L,
                familyName = "Wong Family",
                memberRole = "MEMBER",
                selfProfileId = 77L,
                createdByUserId = 14L,
            ),
        )

        activeProfileManager.switchProfile(99L)
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertFalse(uiState.allowRestrictionEdit == true)
        assertEquals(RestrictionEditAuthorization.READ_ONLY_HINT, uiState.restrictionEditHint)

        viewModel.toggleDietaryRestriction(20L)
        assertFalse(viewModel.uiState.value.selectedRestrictions.containsKey(20L))

        var saved = false
        viewModel.onSave { saved = true }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(saved)
        assertNull(repository.lastSavedSelections)
    }

    // Fake repository for testing
    // Avoid using real repo in tests
    private class FakeDietaryRestrictionRepository : DietaryRestrictionRepository {
        var savedSelections: Map<Long, String> = emptyMap()
        var lastSavedSelections: Map<Long, String>? = null
        var catalog: List<DietaryRestriction> = listOf(
            DietaryRestriction(10L, "HALAL", "Halal", "RELIGIOUS"),
            DietaryRestriction(11L, "VEGETARIAN", "Vegetarian", "RELIGIOUS"),
            DietaryRestriction(20L, "PEANUT", "Peanut Allergy", "ALLERGEN"),
            DietaryRestriction(21L, "MILK", "Milk Allergy", "ALLERGEN"),
            DietaryRestriction(30L, "LOW_CARB", "Low Carb", "DIET")
        )
        var gate: CompletableDeferred<Unit>? = null
        var loadShouldThrow = false
        var saveShouldSucceed = true

        override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> {
            gate?.await()
            if (loadShouldThrow) throw java.io.IOException("network down")
            return catalog
        }

        override suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String> {
            return savedSelections
        }

        override suspend fun saveDietaryRestrictionSelections(profileId: Long, selections: Map<Long, String>): Boolean {
            lastSavedSelections = selections
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
        ): Response<FamilyMeResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun getProfilesByFamilyId(familyId: Long): List<FamilyProfileResponse> = emptyList()

        override suspend fun getActiveProfile(): Response<ActiveProfileResponse> =
            Response.error(404, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun setActiveProfile(
            request: SetActiveProfileRequestBody,
        ): Response<ActiveProfileResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun getFamilyRestrictionSummary(): Response<FamilyRestrictionSumRes> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun searchUserByEmail(email: String): Response<UserSearchResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun createInvitation(
            request: CreateInvitationRequestBody,
        ): Response<InvitationResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun claimInvitation(
            request: ClaimInvitationRequestBody,
        ): Response<FamilyMeResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun listMyInvitations(): Response<List<PendingInvitationResponse>> =
            Response.success(emptyList())

        override suspend fun acceptInvitation(token: String): Response<FamilyMeResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun declineInvitation(token: String): Response<Unit> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun createDependantProfile(
            request: CreateDependantProfileRequestBody,
        ): Response<DependantProfileResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
    }
}
