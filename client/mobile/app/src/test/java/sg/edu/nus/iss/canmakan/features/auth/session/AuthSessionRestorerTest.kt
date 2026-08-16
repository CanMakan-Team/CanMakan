package sg.edu.nus.iss.canmakan.features.auth.session

import com.google.gson.Gson
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.auth.data.AuthFailureType
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRepository
import sg.edu.nus.iss.canmakan.features.auth.data.AuthResult
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser

@DisplayName("UC19 7.6: token-safe session restoration")
class AuthSessionRestorerTest {

    @Test
    fun noSessionAndNoRefreshCookieReturnsUnauthenticatedWithoutNetwork() = runTest {
        val fixture = fixture(hasSession = false, hasCookie = false)

        assertEquals(AuthRestorationResult.Unauthenticated, fixture.restorer.restore())
        assertEquals(0, fixture.repository.currentUserCalls)
        assertEquals(0, fixture.refreshClient.invocations.get())
    }

    @Test
    fun validStoredTokenRestoresBackendUserAndPreservesCurrentToken() = runTest {
        val fixture = fixture()
        val backendUser = AuthenticatedUser(12L, "updated@example.com", AuthRole.ADMIN)
        fixture.repository.resultProvider = { AuthResult.Success(backendUser) }

        val result = fixture.restorer.restore()

        assertEquals(backendUser, authenticatedUser(result))
        assertEquals(TOKEN_A, fixture.store.currentAccessToken())
        assertEquals(backendUser, fixture.store.loadSession()?.user)
        assertEquals(1, fixture.repository.currentUserCalls)
        assertEquals(0, fixture.refreshClient.invocations.get())
    }

    @Test
    fun expiredAccessTokenUsesOneAuthenticatorRefreshAndRestoresWithTokenB() = runTest {
        val fixture = fixture(
            refreshResult = RefreshClientResult.Success(session(TOKEN_B))
        )
        val backendUser = AuthenticatedUser(12L, "backend@example.com", AuthRole.ADMIN)
        fixture.repository.resultProvider = {
            val retry = fixture.authenticator.authenticate(
                null,
                response(401, protectedRequest(TOKEN_A)),
            )
            if (retry?.header(AUTHORIZATION) == "Bearer $TOKEN_B") {
                AuthResult.Success(backendUser)
            } else {
                AuthResult.Failure(AuthFailureType.UNAUTHENTICATED)
            }
        }

        val result = fixture.restorer.restore()

        assertEquals(backendUser, authenticatedUser(result))
        assertEquals(1, fixture.refreshClient.invocations.get())
        assertEquals(TOKEN_B, fixture.store.currentAccessToken())
        assertEquals(backendUser, fixture.store.loadSession()?.user)
    }

    @Test
    fun refreshCookieOnlyStatePerformsExplicitRefreshAndRestoresSession() = runTest {
        val fixture = fixture(
            hasSession = false,
            hasCookie = true,
            refreshResult = RefreshClientResult.Success(session(TOKEN_B)),
        )

        val result = fixture.restorer.restore()

        assertEquals(session(TOKEN_B).user, authenticatedUser(result))
        assertEquals(TOKEN_B, fixture.store.currentAccessToken())
        assertEquals(1, fixture.refreshClient.invocations.get())
        assertEquals(0, fixture.repository.currentUserCalls)
    }

    @Test
    fun refresh401ClearsCredentialsAndRestoresAsUnauthenticated() = runTest {
        val fixture = fixture(refreshResult = RefreshClientResult.Unauthenticated)
        fixture.repository.resultProvider = {
            fixture.authenticator.authenticate(null, response(401, protectedRequest(TOKEN_A)))
            AuthResult.Failure(AuthFailureType.UNAUTHENTICATED)
        }

        assertEquals(AuthRestorationResult.Unauthenticated, fixture.restorer.restore())
        assertNull(fixture.store.loadSession())
        assertFalse(fixture.jar.hasAuthCookieFor(REFRESH_URL))
        assertEquals(1, fixture.refreshClient.invocations.get())
    }

    @Test
    fun refreshNetworkAndServerFailuresAreTemporaryAndPreserveCredentials() = runTest {
        listOf(
            RefreshClientResult.NetworkFailure,
            RefreshClientResult.ServerFailure,
        ).forEach { refreshResult ->
            val fixture = fixture(refreshResult = refreshResult)
            fixture.repository.resultProvider = {
                fixture.authenticator.authenticate(null, response(401, protectedRequest(TOKEN_A)))
                AuthResult.Failure(AuthFailureType.UNAUTHENTICATED)
            }

            assertEquals(
                AuthRestorationResult.TemporarilyUnavailable,
                fixture.restorer.restore(),
            )
            assertEquals(TOKEN_A, fixture.store.currentAccessToken())
            assertTrue(fixture.jar.hasAuthCookieFor(REFRESH_URL))
        }
    }

    @Test
    fun refreshCookieOnlyServerFailureIsTemporaryAndPreservesTheCookie() = runTest {
        val fixture = fixture(
            hasSession = false,
            hasCookie = true,
            refreshResult = RefreshClientResult.ServerFailure,
        )

        assertEquals(
            AuthRestorationResult.TemporarilyUnavailable,
            fixture.restorer.restore(),
        )
        assertNull(fixture.store.loadSession())
        assertTrue(fixture.jar.hasAuthCookieFor(REFRESH_URL))
    }

    @Test
    fun meForbiddenRemainsDistinctAndNeverRefreshesOrClearsCredentials() = runTest {
        val fixture = fixture()
        fixture.repository.resultProvider = {
            AuthResult.Failure(AuthFailureType.FORBIDDEN)
        }

        assertEquals(AuthRestorationResult.Forbidden, fixture.restorer.restore())
        assertEquals(0, fixture.refreshClient.invocations.get())
        assertEquals(TOKEN_A, fixture.store.currentAccessToken())
        assertTrue(fixture.jar.hasAuthCookieFor(REFRESH_URL))
    }

    @Test
    fun meNetworkServerAndInvalidResponsesAreTemporaryWithoutCredentialDeletion() = runTest {
        listOf(
            AuthFailureType.NETWORK,
            AuthFailureType.SERVER,
            AuthFailureType.INVALID_RESPONSE,
        ).forEach { failure ->
            val fixture = fixture()
            fixture.repository.resultProvider = { AuthResult.Failure(failure) }

            assertEquals(
                AuthRestorationResult.TemporarilyUnavailable,
                fixture.restorer.restore(),
            )
            assertEquals(TOKEN_A, fixture.store.currentAccessToken())
            assertTrue(fixture.jar.hasAuthCookieFor(REFRESH_URL))
        }
    }

    @Test
    fun restorationResultAndRestorerStringNeverExposeAccessOrRefreshTokens() = runTest {
        val fixture = fixture()
        val result = fixture.restorer.restore()

        assertFalse(result.toString().contains(TOKEN_A))
        assertFalse(result.toString().contains(TEST_REFRESH_COOKIE))
        assertFalse(fixture.restorer.toString().contains(TOKEN_A))
        assertFalse(
            AuthRestorationResult.Authenticated::class.java.declaredFields.any {
                it.name.contains("token", ignoreCase = true) ||
                    it.name.contains("cookie", ignoreCase = true)
            }
        )
    }

    private fun fixture(
        hasSession: Boolean = true,
        hasCookie: Boolean = true,
        refreshResult: RefreshClientResult = RefreshClientResult.ServerFailure,
    ): Fixture {
        val store = AuthSessionStore(FakeSessionPersistence(), Gson())
        if (hasSession) assertTrue(store.saveSession(session(TOKEN_A)))
        val jar = PersistentRefreshCookieJar(FakeCookiePersistence(), Gson()) { NOW }
        if (hasCookie) jar.saveFromResponse(LOGIN_URL, listOf(refreshCookie()))
        val refreshClient = FakeRefreshClient(refreshResult)
        val coordinator = AuthRefreshCoordinator(refreshClient, store, jar)
        val authenticator = BearerAuthenticator(AuthRequestPolicy(API_BASE_URL), coordinator)
        val repository = FakeAuthRepository()
        val restorer = AuthSessionRestorer(
            authRepository = repository,
            authSessionStore = store,
            refreshCoordinator = coordinator,
            refreshCookieJar = jar,
            refreshUrl = REFRESH_URL,
        )
        return Fixture(store, jar, refreshClient, authenticator, repository, restorer)
    }

    private fun authenticatedUser(result: AuthRestorationResult): AuthenticatedUser {
        return assertInstanceOf(
            AuthRestorationResult.Authenticated::class.java,
            result,
        ).user
    }

    private fun protectedRequest(token: String): Request {
        return Request.Builder()
            .url(ME_URL)
            .header(AUTHORIZATION, "Bearer $token")
            .get()
            .build()
    }

    private fun response(code: Int, request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Test response")
            .build()
    }

    private fun session(accessToken: String): AuthenticatedSession {
        return AuthenticatedSession(
            accessToken = accessToken,
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(12L, "stale@example.com", AuthRole.USER),
        )
    }

    private fun refreshCookie(): Cookie {
        return Cookie.Builder()
            .name(PersistentRefreshCookieJar.REFRESH_COOKIE_NAME)
            .value(TEST_REFRESH_COOKIE)
            .hostOnlyDomain("api.example.test")
            .path("/api/auth")
            .expiresAt(NOW + 60_000)
            .secure()
            .httpOnly()
            .build()
    }

    private class Fixture(
        val store: AuthSessionStore,
        val jar: PersistentRefreshCookieJar,
        val refreshClient: FakeRefreshClient,
        val authenticator: BearerAuthenticator,
        val repository: FakeAuthRepository,
        val restorer: AuthSessionRestorer,
    )

    private class FakeRefreshClient(
        private val result: RefreshClientResult,
    ) : AuthRefreshClient {
        val invocations = AtomicInteger()

        override fun refresh(): RefreshClientResult {
            invocations.incrementAndGet()
            return result
        }
    }

    private class FakeAuthRepository : AuthRepository {
        var currentUserCalls = 0
        var resultProvider: suspend () -> AuthResult<AuthenticatedUser> = {
            AuthResult.Success(AuthenticatedUser(12L, "person@example.com", AuthRole.USER))
        }

        override suspend fun login(
            email: String,
            password: String,
        ): AuthResult<AuthenticatedSession> = error("not used by restoration")

        override suspend fun getCurrentUser(): AuthResult<AuthenticatedUser> {
            currentUserCalls++
            return resultProvider()
        }

        override suspend fun deleteOwnAccount(): AuthResult<Unit> =
            error("not used by restoration")
    }

    private class FakeSessionPersistence : AuthSessionPersistence {
        private var serializedSession: String? = null

        override fun readSession(): String? = serializedSession

        override fun writeSession(serializedSession: String): Boolean {
            this.serializedSession = serializedSession
            return true
        }

        override fun clearSession(): Boolean {
            serializedSession = null
            return true
        }
    }

    private class FakeCookiePersistence : RefreshCookiePersistence {
        private var serializedCookies: String? = null

        override fun readCookies(): String? = serializedCookies

        override fun writeCookies(serializedCookies: String): Boolean {
            this.serializedCookies = serializedCookies
            return true
        }

        override fun clearCookies(): Boolean {
            serializedCookies = null
            return true
        }
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val TOKEN_A = "test-access-token-A"
        const val TOKEN_B = "test-access-token-B"
        const val TEST_REFRESH_COOKIE = "harmless-test-refresh-cookie"
        const val NOW = 1_800_000_000_000L
        val API_BASE_URL = "https://api.example.test/api/".toHttpUrl()
        val LOGIN_URL = requireNotNull(API_BASE_URL.resolve("auth/login"))
        val REFRESH_URL = requireNotNull(API_BASE_URL.resolve("auth/refresh"))
        val ME_URL = requireNotNull(API_BASE_URL.resolve("auth/me"))
    }
}
