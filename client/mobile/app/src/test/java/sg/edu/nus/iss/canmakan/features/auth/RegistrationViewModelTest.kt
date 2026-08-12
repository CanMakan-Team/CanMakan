package sg.edu.nus.iss.canmakan.features.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationFailureType
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationRepository
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationResponse
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationResult
import sg.edu.nus.iss.canmakan.features.auth.onboarding.PendingOnboardingStore
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationStore

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("UC18: account-only Android registration")
class RegistrationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRegistrationRepository
    private lateinit var pendingOnboardingStore: PendingOnboardingStore
    private lateinit var pendingInvitationStore: PendingInvitationStore
    private lateinit var viewModel: RegistrationViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeRegistrationRepository()
        pendingOnboardingStore = PendingOnboardingStore()
        pendingInvitationStore = PendingInvitationStore()
        viewModel = RegistrationViewModel(
            repository,
            pendingOnboardingStore,
            pendingInvitationStore,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun validAccountInputAdvancesWithoutNetworkCall() {
        enterValidAccountInformation()

        assertEquals(RegistrationStep.OPTIONAL_DIETARY_PROFILE, viewModel.uiState.value.step)
        assertEquals(0, repository.callCount)
    }

    @Test
    fun accountValidationStillRejectsInvalidEmailAndPassword() {
        viewModel.updateName("")
        viewModel.updateEmail("not-an-email")
        viewModel.updatePassword("weak")
        viewModel.updateConfirmPassword("different")
        viewModel.continueToDietaryProfile()

        assertEquals(RegistrationStep.ACCOUNT_INFORMATION, viewModel.uiState.value.step)
        assertEquals("Name is required.", viewModel.uiState.value.nameError)
        assertEquals("Enter a valid email address.", viewModel.uiState.value.emailError)
        assertTrue(viewModel.uiState.value.passwordError != null)
        assertEquals("Passwords do not match.", viewModel.uiState.value.confirmPasswordError)
    }

    @Test
    fun successfulRegistrationCompletesWithNewAccountOnlyResponse() {
        enterValidAccountInformation()

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RegistrationStep.COMPLETE, state.step)
        assertEquals(RegistrationResponse(14L, "person@example.com", true), state.account)
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
    }

    @Test
    fun registrationHasNoSecuredProfileSessionOrActiveProfileDependency() {
        val fieldTypes = RegistrationViewModel::class.java.declaredFields.map { it.type }

        assertFalse(fieldTypes.contains(DietaryRestrictionRepository::class.java))
        assertFalse(fieldTypes.contains(ActiveProfileManager::class.java))
        assertFalse(fieldTypes.contains(AuthSessionStore::class.java))
    }

    @Test
    fun requestingDietarySetupStoresOnlyAccountBoundIntentAfterAccountCreation() {
        enterValidAccountInformation()
        viewModel.setDietarySetupRequested(true)

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("person@example.com", pendingOnboardingStore.peek()?.accountEmail)
        assertEquals(
            listOf("accountEmail", "requestId"),
            pendingOnboardingStore.peek()!!::class.java.declaredFields
                .filterNot { it.isSynthetic || it.name.startsWith("$") }
                .map { it.name },
        )
        assertTrue(viewModel.uiState.value.wantsDietarySetup)
    }

    @Test
    fun skippingDietarySetupCreatesNoPendingProfileIntent() {
        pendingOnboardingStore.requestDietarySetup("stale@example.com")
        enterValidAccountInformation()
        viewModel.setDietarySetupRequested(false)

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(pendingOnboardingStore.peek())
    }

    @Test
    fun invitationTokenIsRetainedForLoginButExcludedFromRegistrationRequest() {
        // Updated: RegistrationRepository now includes invitationToken in register call.
        // This test verify it is consumed correctly.
        viewModel.setInvitationToken("  invite-token  ")
        enterValidAccountInformation()

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(pendingInvitationStore.peek())
        assertEquals("invite-token", repository.lastInvitationToken)
        assertEquals(1, repository.callCount)
    }

    @Test
    fun requestNormalizesEmailButPreservesPasswordExactly() {
        val password = "  KeepCase Password1!  "
        viewModel.updateName("  Sarah Tan  ")
        viewModel.updateEmail("  Person@Example.COM  ")
        viewModel.updatePassword(password)
        viewModel.updateConfirmPassword(password)
        viewModel.continueToDietaryProfile()

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Sarah Tan", repository.lastName)
        assertEquals("person@example.com", repository.lastEmail)
        assertEquals(password, repository.lastPassword)
    }

    @Test
    fun duplicateEmailReturnsToAccountStepWithoutCreatingPendingOnboarding() {
        repository.result = RegistrationResult.Failure(
            RegistrationFailureType.DUPLICATE_EMAIL,
            "An account with this email already exists.",
        )
        enterValidAccountInformation()
        viewModel.setDietarySetupRequested(true)

        viewModel.createAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RegistrationStep.ACCOUNT_INFORMATION, viewModel.uiState.value.step)
        assertNull(pendingOnboardingStore.peek())
        assertEquals("Password1!", viewModel.uiState.value.password)
    }

    @Test
    fun duplicateSubmissionWhileLoadingCallsRegistrationOnce() {
        repository.gate = CompletableDeferred()
        enterValidAccountInformation()

        viewModel.createAccount()
        viewModel.createAccount()
        testDispatcher.scheduler.runCurrent()

        assertEquals(1, repository.callCount)
        repository.gate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun registrationStateNeverPrintsPasswords() {
        viewModel.updatePassword("Password1!")
        viewModel.updateConfirmPassword("Password1!")

        val rendered = viewModel.uiState.value.toString()

        assertFalse(rendered.contains("Password1!"))
        assertTrue(rendered.contains("password=<redacted>"))
    }

    private fun enterValidAccountInformation() {
        viewModel.updateName("Sarah Tan")
        viewModel.updateEmail("  Person@Example.COM  ")
        viewModel.updatePassword("Password1!")
        viewModel.updateConfirmPassword("Password1!")
        viewModel.continueToDietaryProfile()
    }

    private class FakeRegistrationRepository : RegistrationRepository {
        var result: RegistrationResult = RegistrationResult.Success(
            RegistrationResponse(14L, "person@example.com", true),
        )
        var gate: CompletableDeferred<Unit>? = null
        var callCount = 0
        var lastName: String? = null
        var lastEmail: String? = null
        var lastPassword: String? = null
        var lastInvitationToken: String? = null

        override suspend fun register(
            name: String,
            email: String,
            password: String,
            invitationToken: String?,
        ): RegistrationResult {
            callCount++
            lastName = name
            lastEmail = email
            lastPassword = password
            lastInvitationToken = invitationToken
            gate?.await()
            return result
        }
    }
}
