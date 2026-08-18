package sg.edu.nus.iss.canmakan.features.auth

import com.google.gson.Gson
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
import sg.edu.nus.iss.canmakan.features.auth.data.AuthFailureType
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRepository
import sg.edu.nus.iss.canmakan.features.auth.data.AuthResult
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.data.InvitationPreviewResponse
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationFailureType
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationRepository
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationResponse
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationResult
import sg.edu.nus.iss.canmakan.features.auth.onboarding.PendingOnboardingStore
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionPersistence
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data.DietaryRestrictionRepository
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationStore

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("UC18: account-only registration followed by normal login")
class RegistrationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var registrationRepository: FakeRegistrationRepository
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var pendingOnboardingStore: PendingOnboardingStore
    private lateinit var pendingInvitationStore: PendingInvitationStore
    private lateinit var viewModel: RegistrationViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        registrationRepository = FakeRegistrationRepository()
        authRepository = FakeAuthRepository()
        sessionStore = AuthSessionStore(FakeSessionPersistence(), Gson())
        pendingOnboardingStore = PendingOnboardingStore()
        pendingInvitationStore = PendingInvitationStore()
        viewModel = RegistrationViewModel(
            registrationRepository,
            authRepository,
            sessionStore,
            pendingOnboardingStore,
            pendingInvitationStore,
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun validSubmissionRegistersThenUsesAuthoritativeLoginAndStoresPendingProfileName() {
        enterValidAccountInformation()

        viewModel.createAccount()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(RegistrationStep.COMPLETE, state.step)
        assertEquals(1, registrationRepository.callCount)
        assertEquals(1, authRepository.loginCalls)
        assertEquals("person@example.com", authRepository.lastEmail)
        assertEquals("Password1!", authRepository.lastPassword)
        assertEquals(14L, sessionStore.authenticatedUser.value?.userId)
        assertEquals("Person Name", pendingOnboardingStore.peek()?.accountName)
        assertEquals("person@example.com", pendingOnboardingStore.peek()?.accountEmail)
        assertEquals(14L, state.authenticatedUser?.userId)
        assertEquals("", state.password)
    }

    @Test
    fun automaticLoginFailureKeepsAccountPendingSetupAndCannotRegisterTwice() {
        authRepository.result = AuthResult.Failure(AuthFailureType.NETWORK)
        enterValidAccountInformation()

        viewModel.createAccount()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.createAccount()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, registrationRepository.callCount)
        assertEquals(RegistrationResponse(14L, "person@example.com", true), state.account)
        assertTrue(state.accountCreatedButLoginFailed)
        assertEquals(RegistrationViewModel.AUTO_LOGIN_FAILURE_MESSAGE, state.registrationError)
        assertNull(state.authenticatedUser)
        assertNull(sessionStore.authenticatedUser.value)
        assertEquals("Person Name", pendingOnboardingStore.peek()?.accountName)
    }

    @Test
    fun duplicateEmailShowsApprovedMessageAndDoesNotLoginOrCreateOnboarding() {
        registrationRepository.result = RegistrationResult.Failure(
            RegistrationFailureType.DUPLICATE_EMAIL,
            "An account with this email already exists.",
        )
        enterValidAccountInformation()

        viewModel.createAccount()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("An account with this email already exists.", viewModel.uiState.value.registrationError)
        assertEquals(RegistrationFailureType.DUPLICATE_EMAIL, viewModel.uiState.value.registrationFailureType)
        assertEquals(0, authRepository.loginCalls)
        assertNull(pendingOnboardingStore.peek())
        assertEquals("Password1!", viewModel.uiState.value.password)
    }

    @Test
    fun invalidInputDoesNotCallRegistration() {
        viewModel.updateName("")
        viewModel.updateEmail("not-an-email")
        viewModel.updatePassword("weak")
        viewModel.updateConfirmPassword("different")

        viewModel.createAccount()

        assertEquals(0, registrationRepository.callCount)
        assertEquals("Profile Name is required.", viewModel.uiState.value.nameError)
        assertEquals("Enter a valid email address.", viewModel.uiState.value.emailError)
        assertEquals("Passwords do not match.", viewModel.uiState.value.confirmPasswordError)
    }

    @Test
    fun duplicateSubmissionWhileLoadingCallsRegistrationOnce() {
        registrationRepository.gate = CompletableDeferred()
        enterValidAccountInformation()

        viewModel.createAccount()
        viewModel.createAccount()
        dispatcher.scheduler.runCurrent()

        assertEquals(1, registrationRepository.callCount)
        registrationRepository.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun requestNormalizesEmailPreservesPasswordAndDoesNotSendProfileName() {
        val password = "  KeepCase Password1!  "
        viewModel.updateName("  Person Name  ")
        viewModel.updateEmail("  Person@Example.COM  ")
        viewModel.updatePassword(password)
        viewModel.updateConfirmPassword(password)

        viewModel.createAccount()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("person@example.com", registrationRepository.lastEmail)
        assertEquals(password, registrationRepository.lastPassword)
        assertEquals("Person Name", pendingOnboardingStore.peek()?.accountName)
    }

    @Test
    fun invitationTokenRemainsPendingAcrossRegistrationAndAutomaticLogin() {
        viewModel.setInvitationToken("  invite-token  ")
        enterValidAccountInformation()

        viewModel.createAccount()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("invite-token", pendingInvitationStore.peek())
        assertEquals("invite-token", registrationRepository.lastInvitationToken)
    }

    @Test
    fun invitationPreviewLocksEmailToInvitedAddress() {
        registrationRepository.preview = InvitationPreviewResponse(
            invitedEmail = "jamie@example.com",
            familyName = "Wong Family",
            expired = false,
        )
        viewModel.setInvitationToken("invite-token")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("jamie@example.com", viewModel.uiState.value.email)
        assertTrue(viewModel.uiState.value.emailLocked)
        viewModel.updateEmail("other@example.com")
        assertEquals("jamie@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun registrationDoesNotDependOnDietaryOrActiveProfileRepositories() {
        val fieldTypes = RegistrationViewModel::class.java.declaredFields.map { it.type }

        assertFalse(fieldTypes.contains(DietaryRestrictionRepository::class.java))
        assertFalse(fieldTypes.contains(ActiveProfileManager::class.java))
        assertTrue(fieldTypes.contains(AuthSessionStore::class.java))
    }

    @Test
    fun stateNeverPrintsPasswords() {
        viewModel.updatePassword("Password1!")
        viewModel.updateConfirmPassword("Password1!")

        assertFalse(viewModel.uiState.value.toString().contains("Password1!"))
    }

    @Test
    fun validationCoversLengthPasswordAndConfirmEdges() {
        viewModel.updateName("n".repeat(101))
        viewModel.updateEmail("e".repeat(256) + "@x.y")
        viewModel.updatePassword("   ")
        viewModel.updateConfirmPassword("")
        viewModel.createAccount()
        assertEquals("Profile Name must not exceed 100 characters.", viewModel.uiState.value.nameError)
        assertEquals("Email must not exceed 255 characters.", viewModel.uiState.value.emailError)
        assertEquals("Password is required.", viewModel.uiState.value.passwordError)
        assertEquals("Confirm your password.", viewModel.uiState.value.confirmPasswordError)

        viewModel.updateName("Person")
        viewModel.updateEmail("person@example.com")
        viewModel.updatePassword("short")
        viewModel.updateConfirmPassword("short")
        viewModel.createAccount()
        assertEquals("Password must be at least 8 characters.", viewModel.uiState.value.passwordError)

        viewModel.updatePassword("Password1")
        viewModel.updateConfirmPassword("Password1")
        viewModel.createAccount()
        assertEquals(
            "Password must be at least 8 characters and include uppercase, lowercase, a number, and a special character.",
            viewModel.uiState.value.passwordError,
        )

        viewModel.updatePassword("A1!" + "é".repeat(35))
        viewModel.updateConfirmPassword("A1!" + "é".repeat(35))
        viewModel.createAccount()
        assertEquals("Password must not exceed 72 UTF-8 bytes.", viewModel.uiState.value.passwordError)
        assertEquals(0, registrationRepository.callCount)
    }

    private fun enterValidAccountInformation() {
        viewModel.updateName("  Person Name  ")
        viewModel.updateEmail("  Person@Example.COM  ")
        viewModel.updatePassword("Password1!")
        viewModel.updateConfirmPassword("Password1!")
    }

    private class FakeRegistrationRepository : RegistrationRepository {
        var result: RegistrationResult = RegistrationResult.Success(
            RegistrationResponse(14L, "person@example.com", true),
        )
        var gate: CompletableDeferred<Unit>? = null
        var callCount = 0
        var lastEmail: String? = null
        var lastPassword: String? = null
        var lastInvitationToken: String? = null
        var preview: InvitationPreviewResponse? = null

        override suspend fun register(
            email: String,
            password: String,
            invitationToken: String?,
        ): RegistrationResult {
            callCount++
            lastEmail = email
            lastPassword = password
            lastInvitationToken = invitationToken
            gate?.await()
            return result
        }

        override suspend fun previewInvitation(invitationToken: String): InvitationPreviewResponse? {
            return preview
        }
    }

    private class FakeAuthRepository : AuthRepository {
        var result: AuthResult<AuthenticatedSession> = AuthResult.Success(validSession())
        var loginCalls = 0
        var lastEmail: String? = null
        var lastPassword: String? = null

        override suspend fun login(email: String, password: String): AuthResult<AuthenticatedSession> {
            loginCalls++
            lastEmail = email
            lastPassword = password
            return result
        }

        override suspend fun getCurrentUser(): AuthResult<AuthenticatedUser> =
            AuthResult.Failure(AuthFailureType.UNAUTHENTICATED)

        override suspend fun deleteOwnAccount(): AuthResult<Unit> =
            AuthResult.Failure(AuthFailureType.UNAUTHENTICATED)
    }

    private class FakeSessionPersistence : AuthSessionPersistence {
        private var value: String? = null
        override fun readSession(): String? = value
        override fun writeSession(serializedSession: String): Boolean {
            value = serializedSession
            return true
        }
        override fun clearSession(): Boolean {
            value = null
            return true
        }
    }

    private companion object {
        fun validSession() = AuthenticatedSession(
            accessToken = "access-token",
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(14L, "person@example.com", AuthRole.USER),
        )
    }
}
