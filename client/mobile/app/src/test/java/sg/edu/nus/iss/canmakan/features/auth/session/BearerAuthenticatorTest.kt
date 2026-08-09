package sg.edu.nus.iss.canmakan.features.auth.session

import com.google.gson.Gson
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser

@DisplayName("UC19 7.6: origin-safe one-retry Bearer Authenticator")
class BearerAuthenticatorTest {
    private lateinit var store: AuthSessionStore
    private lateinit var jar: PersistentRefreshCookieJar

    @BeforeEach
    fun setUp() {
        store = AuthSessionStore(FakeSessionPersistence(), Gson())
        assertTrue(store.saveSession(session(TOKEN_A)))
        jar = PersistentRefreshCookieJar(FakeCookiePersistence(), Gson()) { NOW }
        jar.saveFromResponse(LOGIN_URL, listOf(refreshCookie()))
    }

    @Test
    fun eligibleFirstParty401RefreshesAndReplacesAuthorizationExactlyOnce() {
        val fixture = authenticator(RefreshClientResult.Success(session(TOKEN_B)))

        val retry = requireNotNull(
            fixture.authenticator.authenticate(null, response(401, protectedRequest()))
        )

        assertEquals("Bearer $TOKEN_B", retry.header(AUTHORIZATION))
        assertEquals(1, retry.headers.values(AUTHORIZATION).size)
        assertEquals(1, fixture.client.invocations.get())
        assertEquals(TOKEN_B, store.currentAccessToken())
    }

    @Test
    fun retriedRequestStillReturning401StopsWithoutASecondRefresh() {
        val fixture = authenticator(RefreshClientResult.Success(session(TOKEN_B)))
        val firstRetry = requireNotNull(
            fixture.authenticator.authenticate(null, response(401, protectedRequest()))
        )

        val secondRetry = fixture.authenticator.authenticate(null, response(401, firstRetry))

        assertNull(secondRetry)
        assertEquals(1, fixture.client.invocations.get())
    }

    @Test
    fun noBearerOrMalformedBearer401NeverRefreshes() {
        listOf(
            Request.Builder().url(PROTECTED_URL).get().build(),
            protectedRequest().newBuilder().header(AUTHORIZATION, "Basic test-value").build(),
            protectedRequest().newBuilder().header(AUTHORIZATION, "Bearer").build(),
        ).forEach { request ->
            val fixture = authenticator(RefreshClientResult.Success(session(TOKEN_B)))

            assertNull(fixture.authenticator.authenticate(null, response(401, request)))
            assertEquals(0, fixture.client.invocations.get())
        }
    }

    @Test
    fun foreignHostSchemeAndPort401NeverRefresh() {
        listOf(
            "https://foreign.example.test:8443/api/auth/me",
            "http://api.example.test:8443/api/auth/me",
            "https://api.example.test:9443/api/auth/me",
        ).forEach { url ->
            val fixture = authenticator(RefreshClientResult.Success(session(TOKEN_B)))
            val request = Request.Builder()
                .url(url)
                .header(AUTHORIZATION, "Bearer $TOKEN_A")
                .get()
                .build()

            assertNull(fixture.authenticator.authenticate(null, response(401, request)))
            assertEquals(0, fixture.client.invocations.get())
            assertEquals(TOKEN_A, store.currentAccessToken())
            assertTrue(jar.hasAuthCookieFor(REFRESH_URL))
        }
    }

    @Test
    fun publicAuthEndpoint401NeverRefreshes() {
        listOf("auth/register", "auth/login", "auth/refresh", "auth/logout").forEach { route ->
            val fixture = authenticator(RefreshClientResult.Success(session(TOKEN_B)))
            val request = Request.Builder()
                .url(requireNotNull(API_BASE_URL.resolve(route)))
                .header(AUTHORIZATION, "Bearer $TOKEN_A")
                .post(ByteArray(0).toRequestBody(null))
                .build()

            assertNull(fixture.authenticator.authenticate(null, response(401, request)))
            assertEquals(0, fixture.client.invocations.get())
        }
    }

    @Test
    fun forbiddenResponseDoesNotRefreshClearOrRetry() {
        val fixture = authenticator(RefreshClientResult.Success(session(TOKEN_B)))

        assertNull(
            fixture.authenticator.authenticate(null, response(403, protectedRequest()))
        )
        assertEquals(0, fixture.client.invocations.get())
        assertEquals(TOKEN_A, store.currentAccessToken())
        assertTrue(jar.hasAuthCookieFor(REFRESH_URL))
    }

    @Test
    fun authenticatorStringRepresentationDoesNotExposeTokenOrCoordinatorState() {
        val fixture = authenticator(RefreshClientResult.Success(session(TOKEN_B)))

        assertFalse(fixture.authenticator.toString().contains(TOKEN_A))
        assertFalse(fixture.authenticator.toString().contains(TOKEN_B))
        assertFalse(fixture.authenticator.toString().contains(jar.toString()))
    }

    private fun authenticator(result: RefreshClientResult): Fixture {
        val client = FakeRefreshClient(result)
        val coordinator = AuthRefreshCoordinator(client, store, jar)
        return Fixture(
            authenticator = BearerAuthenticator(AuthRequestPolicy(API_BASE_URL), coordinator),
            client = client,
        )
    }

    private fun protectedRequest(): Request {
        return Request.Builder()
            .url(PROTECTED_URL)
            .header(AUTHORIZATION, "Bearer $TOKEN_A")
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
            user = AuthenticatedUser(12L, "person@example.com", AuthRole.USER),
        )
    }

    private fun refreshCookie(): Cookie {
        return Cookie.Builder()
            .name(PersistentRefreshCookieJar.REFRESH_COOKIE_NAME)
            .value("harmless-test-refresh-cookie")
            .hostOnlyDomain("api.example.test")
            .path("/api/auth")
            .expiresAt(NOW + 60_000)
            .secure()
            .httpOnly()
            .build()
    }

    private data class Fixture(
        val authenticator: BearerAuthenticator,
        val client: FakeRefreshClient,
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
        const val NOW = 1_800_000_000_000L
        val API_BASE_URL = "https://api.example.test:8443/api/".toHttpUrl()
        val PROTECTED_URL = requireNotNull(API_BASE_URL.resolve("auth/me"))
        val LOGIN_URL = requireNotNull(API_BASE_URL.resolve("auth/login"))
        val REFRESH_URL = requireNotNull(API_BASE_URL.resolve("auth/refresh"))
    }
}
