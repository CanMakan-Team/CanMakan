package sg.edu.nus.iss.canmakan.features.auth

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
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
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionPersistence
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationStore

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("UC19 7.4: Android Login ViewModel")
class LoginViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAuthRepository
    private lateinit var persistence: FakeAuthSessionPersistence
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var pendingInvitationStore: PendingInvitationStore
    private lateinit var viewModel: LoginViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeAuthRepository()
        persistence = FakeAuthSessionPersistence()
        sessionStore = AuthSessionStore(persistence, Gson())
        pendingInvitationStore = PendingInvitationStore()
        viewModel = LoginViewModel(
            repository,
            sessionStore,
            pendingInvitationStore,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateContainsNoCredentialsErrorsLoadingOrSuccess() {
        assertEquals(LoginUiState(), viewModel.uiState.value)
        assertNull(sessionStore.currentAccessToken())
    }

    @Test
    fun emptyEmailIsRejectedBeforeRepositoryInvocation() {
        viewModel.updatePassword("Password123!")

        viewModel.login()

        assertEquals(LoginViewModel.EMAIL_REQUIRED_MESSAGE, viewModel.uiState.value.emailError)
        assertEquals(0, repository.loginCalls)
    }

    @Test
    fun invalidEmailIsRejectedBeforeRepositoryInvocation() {
        viewModel.updateEmail("not-an-email")
        viewModel.updatePassword("Password123!")

        viewModel.login()

        assertEquals(LoginViewModel.EMAIL_INVALID_MESSAGE, viewModel.uiState.value.emailError)
        assertEquals(0, repository.loginCalls)
    }

    @Test
    fun blankPasswordIsRejectedWithoutRegistrationComplexityRules() {
        viewModel.updateEmail("person@example.com")
        viewModel.updatePassword("   ")

        viewModel.login()

        assertEquals(LoginViewModel.PASSWORD_REQUIRED_MESSAGE, viewModel.uiState.value.passwordError)
        assertEquals(0, repository.loginCalls)
    }

    @Test
    fun emailIsTrimmedAndLowercasedWhilePasswordIsForwardedExactly() {
        val exactPassword = "  KeepCase Password!  "
        enterCredentials(email = "  Person@Example.COM  ", password = exactPassword)

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("person@example.com", repository.lastEmail)
        assertEquals(exactPassword, repository.lastPassword)
    }

    @Test
    fun successfulLoginCallsRepositoryOnceAndPersistsTheSession() {
        enterCredentials()

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.loginCalls)
        assertEquals(TEST_ACCESS_TOKEN, sessionStore.currentAccessToken())
        assertTrue(persistence.serializedSession != null)
        assertEquals(12L, viewModel.uiState.value.authenticatedUser?.userId)
        assertEquals("", viewModel.uiState.value.password)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun loginRetainsInvitationForTheSinglePostLoginOrchestrator() {
        viewModel.setInvitationToken("  invite-token  ")
        enterCredentials()

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("invite-token", pendingInvitationStore.peek())
        assertEquals(12L, viewModel.uiState.value.authenticatedUser?.userId)
    }

    @Test
    fun successSignalContainsSafeUserMetadataButNeverTheAccessToken() {
        enterCredentials()

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AuthRole.USER, state.authenticatedUser?.role)
        assertFalse(state.toString().contains(TEST_ACCESS_TOKEN))
        assertFalse(
            LoginUiState::class.java.declaredFields.any {
                it.name.contains("accessToken", ignoreCase = true) ||
                    it.name.contains("session", ignoreCase = true)
            }
        )
    }

    @Test
    fun canonicalAdminRoleIsPreservedWithoutFamilyRoleMapping() {
        repository.result = AuthResult.Success(validSession(role = AuthRole.ADMIN))
        enterCredentials()

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthRole.ADMIN, viewModel.uiState.value.authenticatedUser?.role)
    }

    @Test
    fun sessionPersistenceFailureDoesNotReportAuthenticationSuccess() {
        persistence.writeSucceeds = false
        enterCredentials()

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.authenticatedUser)
        assertNull(sessionStore.currentAccessToken())
        assertEquals(
            LoginViewModel.SESSION_ESTABLISHMENT_MESSAGE,
            viewModel.uiState.value.loginError,
        )
    }

    @Test
    fun invalidCredentialsUseOneAccountEnumerationSafeMessage() {
        repository.result = AuthResult.Failure(AuthFailureType.INVALID_CREDENTIALS)
        enterCredentials()

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LoginViewModel.INVALID_CREDENTIALS_MESSAGE, viewModel.uiState.value.loginError)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun unauthenticatedFailureUsesTheSameAccountEnumerationSafeMessage() {
        assertFailureMessage(
            AuthFailureType.UNAUTHENTICATED,
            LoginViewModel.INVALID_CREDENTIALS_MESSAGE,
        )
    }

    @Test
    fun successfulLoginIgnoresFurtherEditsAndDuplicateLoginAttempts() {
        enterCredentials()
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repository.loginCalls)

        viewModel.updateEmail("other@example.com")
        viewModel.updatePassword("DifferentPassword1!")
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.loginCalls)
        assertEquals("person@example.com", viewModel.uiState.value.email)
        assertEquals("", viewModel.uiState.value.password)
        assertEquals(12L, viewModel.uiState.value.authenticatedUser?.userId)
    }

    @Test
    fun malformedRequestUsesSafeValidationMessage() {
        assertFailureMessage(
            AuthFailureType.MALFORMED_REQUEST,
            LoginViewModel.MALFORMED_REQUEST_MESSAGE,
        )
    }

    @Test
    fun forbiddenUsesSafeAccountAccessMessage() {
        assertFailureMessage(AuthFailureType.FORBIDDEN, LoginViewModel.FORBIDDEN_MESSAGE)
    }

    @Test
    fun networkFailureUsesConnectivityMessage() {
        assertFailureMessage(AuthFailureType.NETWORK, LoginViewModel.NETWORK_MESSAGE)
    }

    @Test
    fun serverFailureUsesTemporaryServiceMessage() {
        assertFailureMessage(AuthFailureType.SERVER, LoginViewModel.SERVER_MESSAGE)
    }

    @Test
    fun invalidResponseUsesGenericMessage() {
        assertFailureMessage(AuthFailureType.INVALID_RESPONSE, LoginViewModel.INVALID_RESPONSE_MESSAGE)
    }

    @Test
    fun duplicateTapsWhileLoadingStartOnlyOneBackendLogin() {
        repository.gate = CompletableDeferred()
        enterCredentials()

        viewModel.login()
        assertTrue(viewModel.uiState.value.isSubmitting)
        viewModel.login()
        testDispatcher.scheduler.runCurrent()

        assertEquals(1, repository.loginCalls)
        repository.gate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun loadingStateIsSetBeforeTheRequestAndClearedAfterFailure() {
        repository.result = AuthResult.Failure(AuthFailureType.NETWORK)
        repository.gate = CompletableDeferred()
        enterCredentials()

        viewModel.login()
        assertTrue(viewModel.uiState.value.isSubmitting)
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isSubmitting)

        repository.gate?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun editingAFieldClearsItsValidationAndStalePageError() {
        repository.result = AuthResult.Failure(AuthFailureType.INVALID_CREDENTIALS)
        enterCredentials()
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loginError != null)

        viewModel.updateEmail("new@example.com")

        assertNull(viewModel.uiState.value.emailError)
        assertNull(viewModel.uiState.value.loginError)
    }

    @Test
    fun uiStateStringRedactsPasswordAndNeverIncludesSessionToken() {
        val password = "Do Not Print Password!"
        enterCredentials(password = password)

        val beforeLogin = viewModel.uiState.value.toString()
        assertFalse(beforeLogin.contains(password))
        assertTrue(beforeLogin.contains("password=<redacted>"))

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.toString().contains(TEST_ACCESS_TOKEN))
    }

    @Test
    fun repositoryExceptionIsMappedWithoutExposingItsMessage() {
        repository.exception = IllegalStateException("accessToken=do-not-expose")
        enterCredentials()

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LoginViewModel.INVALID_RESPONSE_MESSAGE, viewModel.uiState.value.loginError)
        assertFalse(viewModel.uiState.value.toString().contains("do-not-expose"))
    }

    @Test
    fun cancellationIsRethrownAndLoadingIsClearedWithoutAFalseError() {
        repository.exception = CancellationException("cancelled")
        enterCredentials()

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertNull(viewModel.uiState.value.loginError)
        assertNull(viewModel.uiState.value.authenticatedUser)
    }

    private fun assertFailureMessage(type: AuthFailureType, expectedMessage: String) {
        repository.result = AuthResult.Failure(type)
        enterCredentials()

        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(expectedMessage, viewModel.uiState.value.loginError)
        assertNull(viewModel.uiState.value.authenticatedUser)
        assertNull(sessionStore.currentAccessToken())
    }

    private fun enterCredentials(
        email: String = "person@example.com",
        password: String = "Password123!",
    ) {
        viewModel.updateEmail(email)
        viewModel.updatePassword(password)
    }

    private class FakeAuthRepository : AuthRepository {
        var result: AuthResult<AuthenticatedSession> = AuthResult.Success(validSession())
        var exception: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var loginCalls = 0
        var lastEmail: String? = null
        var lastPassword: String? = null

        override suspend fun login(email: String, password: String): AuthResult<AuthenticatedSession> {
            loginCalls++
            lastEmail = email
            lastPassword = password
            gate?.await()
            exception?.let { throw it }
            return result
        }

        override suspend fun getCurrentUser(): AuthResult<AuthenticatedUser> {
            error("/me is outside LoginViewModel scope")
        }

        override suspend fun deleteOwnAccount(): AuthResult<Unit> {
            error("deleteOwnAccount is outside LoginViewModel scope")
        }
    }

    private class FakeAuthSessionPersistence : AuthSessionPersistence {
        var serializedSession: String? = null
        var writeSucceeds = true

        override fun readSession(): String? = serializedSession

        override fun writeSession(serializedSession: String): Boolean {
            if (writeSucceeds) this.serializedSession = serializedSession
            return writeSucceeds
        }

        override fun clearSession(): Boolean {
            serializedSession = null
            return true
        }
    }

    private companion object {
        const val TEST_ACCESS_TOKEN = "test-access-token"

        fun validSession(role: AuthRole = AuthRole.USER): AuthenticatedSession {
            return AuthenticatedSession(
                accessToken = TEST_ACCESS_TOKEN,
                tokenType = "Bearer",
                expiresIn = 900,
                user = AuthenticatedUser(12L, "person@example.com", role),
            )
        }
    }
}
