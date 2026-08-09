package sg.edu.nus.iss.canmakan.features.auth.session

import com.google.gson.Gson
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser

@DisplayName("UC19 7.6: single-flight refresh coordinator")
class AuthRefreshCoordinatorTest {

    @Test
    fun threeConcurrentFailuresWithTokenAUseOneRefreshAndAllReceiveTokenB() {
        val refreshStarted = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        val client = FakeRefreshClient(RefreshClientResult.Success(session(TOKEN_B))).apply {
            started = refreshStarted
            release = releaseRefresh
        }
        val fixture = fixture(client)
        val authenticator = BearerAuthenticator(AuthRequestPolicy(API_BASE_URL), fixture.coordinator)
        val executor = Executors.newFixedThreadPool(3)
        val callersReady = CyclicBarrier(3)

        try {
            val futures = List(3) {
                executor.submit<String> {
                    callersReady.await(5, TimeUnit.SECONDS)
                    val retry = requireNotNull(
                        authenticator.authenticate(null, unauthorizedResponse())
                    )
                    requireNotNull(retry.header(AUTHORIZATION))
                }
            }

            assertTrue(refreshStarted.await(5, TimeUnit.SECONDS))
            releaseRefresh.countDown()

            val retryAuthorizations = futures.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(
                listOf("Bearer $TOKEN_B", "Bearer $TOKEN_B", "Bearer $TOKEN_B"),
                retryAuthorizations,
            )
            assertEquals(1, client.invocations.get())
            assertFalse(retryAuthorizations.contains("Bearer $TOKEN_A"))
            assertEquals(TOKEN_B, fixture.store.currentAccessToken())
        } finally {
            releaseRefresh.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun currentTokenRecheckReturnsBWithoutAnotherRefresh() {
        val client = FakeRefreshClient(RefreshClientResult.ServerFailure)
        val fixture = fixture(client)
        assertTrue(fixture.store.saveSession(session(TOKEN_B)))

        assertEquals(
            TOKEN_B,
            retryToken(fixture.coordinator.recoverFromUnauthorized(TOKEN_A)),
        )
        assertEquals(0, client.invocations.get())
    }

    @Test
    fun successfulRefreshPersistsTheValidatedReplacementSession() {
        val client = FakeRefreshClient(RefreshClientResult.Success(session(TOKEN_B)))
        val fixture = fixture(client)

        assertEquals(
            TOKEN_B,
            retryToken(fixture.coordinator.recoverFromUnauthorized(TOKEN_A)),
        )
        assertEquals(TOKEN_B, fixture.store.currentAccessToken())
        assertEquals(1, client.invocations.get())
    }

    @Test
    fun confirmedOrUnsafeRefreshFailuresClearSessionAndCookie() {
        listOf(
            RefreshClientResult.Unauthenticated to RefreshDecision.Unauthenticated,
            RefreshClientResult.Forbidden to RefreshDecision.Forbidden,
            RefreshClientResult.InvalidResponse to RefreshDecision.InvalidResponse,
        ).forEach { (clientResult, expectedDecision) ->
            val fixture = fixture(FakeRefreshClient(clientResult))

            val decision = fixture.coordinator.recoverFromUnauthorized(TOKEN_A)

            assertEquals(expectedDecision, decision)
            assertNull(fixture.store.loadSession())
            assertFalse(fixture.jar.hasAuthCookieFor(REFRESH_URL))
        }
    }

    @Test
    fun networkAndServerFailuresPreserveSessionAndRefreshCookie() {
        listOf(
            RefreshClientResult.NetworkFailure,
            RefreshClientResult.ServerFailure,
        ).forEach { clientResult ->
            val fixture = fixture(FakeRefreshClient(clientResult))

            assertEquals(
                RefreshDecision.TemporarilyUnavailable,
                fixture.coordinator.recoverFromUnauthorized(TOKEN_A),
            )
            assertEquals(TOKEN_A, fixture.store.currentAccessToken())
            assertTrue(fixture.jar.hasAuthCookieFor(REFRESH_URL))
        }
    }

    @Test
    fun sessionPersistenceFailureAfterRotatingRefreshFailsClosed() {
        val fixture = fixture(
            FakeRefreshClient(RefreshClientResult.Success(session(TOKEN_B)))
        )
        fixture.sessionPersistence.writeSucceeds = false

        assertEquals(
            RefreshDecision.InvalidResponse,
            fixture.coordinator.recoverFromUnauthorized(TOKEN_A),
        )
        assertNull(fixture.store.loadSession())
        assertFalse(fixture.jar.hasAuthCookieFor(REFRESH_URL))
    }

    @Test
    fun unexpectedRefreshExceptionReleasesTheLockForALaterAttempt() {
        val sequenceClient = SequenceRefreshClient(
            ArrayDeque<() -> RefreshClientResult>().apply {
                add { throw IllegalStateException("test failure") }
                add { RefreshClientResult.Success(session(TOKEN_B)) }
            }
        )
        val fixture = fixture(sequenceClient)

        assertEquals(
            RefreshDecision.TemporarilyUnavailable,
            fixture.coordinator.recoverFromUnauthorized(TOKEN_A),
        )
        assertEquals(TOKEN_A, fixture.store.currentAccessToken())
        assertEquals(
            TOKEN_B,
            retryToken(fixture.coordinator.recoverFromUnauthorized(TOKEN_A)),
        )
        assertEquals(2, sequenceClient.invocations.get())
    }

    @Test
    fun refreshOutcomesAndCoordinatorStringRepresentationsRedactTokens() {
        val success = RefreshClientResult.Success(session(TOKEN_B))
        val fixture = fixture(FakeRefreshClient(success))
        val retry = fixture.coordinator.recoverFromUnauthorized(TOKEN_A)

        assertFalse(success.toString().contains(TOKEN_B))
        assertFalse(retry.toString().contains(TOKEN_B))
        assertFalse(fixture.coordinator.toString().contains(TOKEN_A))
        assertFalse(fixture.coordinator.toString().contains(TOKEN_B))
    }

    private fun fixture(client: AuthRefreshClient): Fixture {
        val sessionPersistence = FakeSessionPersistence()
        val store = AuthSessionStore(sessionPersistence, Gson())
        assertTrue(store.saveSession(session(TOKEN_A)))
        val jar = PersistentRefreshCookieJar(FakeCookiePersistence(), Gson()) { NOW }
        jar.saveFromResponse(LOGIN_URL, listOf(refreshCookie()))
        return Fixture(
            store = store,
            jar = jar,
            sessionPersistence = sessionPersistence,
            coordinator = AuthRefreshCoordinator(client, store, jar),
        )
    }

    private fun retryToken(decision: RefreshDecision): String {
        return assertInstanceOf(RefreshDecision.RetryWithToken::class.java, decision).accessToken
    }

    private fun unauthorizedResponse(): Response {
        val request = Request.Builder()
            .url(requireNotNull(API_BASE_URL.resolve("auth/me")))
            .header(AUTHORIZATION, "Bearer $TOKEN_A")
            .get()
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
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
        val store: AuthSessionStore,
        val jar: PersistentRefreshCookieJar,
        val sessionPersistence: FakeSessionPersistence,
        val coordinator: AuthRefreshCoordinator,
    )

    private class FakeRefreshClient(
        private val result: RefreshClientResult,
    ) : AuthRefreshClient {
        val invocations = AtomicInteger()
        var started: CountDownLatch? = null
        var release: CountDownLatch? = null

        override fun refresh(): RefreshClientResult {
            invocations.incrementAndGet()
            started?.countDown()
            release?.await(5, TimeUnit.SECONDS)
            return result
        }
    }

    private class SequenceRefreshClient(
        private val results: ArrayDeque<() -> RefreshClientResult>,
    ) : AuthRefreshClient {
        val invocations = AtomicInteger()

        override fun refresh(): RefreshClientResult {
            invocations.incrementAndGet()
            return results.removeFirst().invoke()
        }
    }

    private class FakeSessionPersistence : AuthSessionPersistence {
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
        const val TOKEN_A = "test-access-token-A"
        const val TOKEN_B = "test-access-token-B"
        const val AUTHORIZATION = "Authorization"
        const val NOW = 1_800_000_000_000L
        val API_BASE_URL = "https://api.example.test/api/".toHttpUrl()
        val LOGIN_URL = requireNotNull(API_BASE_URL.resolve("auth/login"))
        val REFRESH_URL = requireNotNull(API_BASE_URL.resolve("auth/refresh"))
    }
}
