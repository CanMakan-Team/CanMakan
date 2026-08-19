package sg.edu.nus.iss.canmakan.features.auth.session

import com.google.gson.Gson
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser

@DisplayName("UC19 7.7: serialized user logout")
class AuthLogoutCoordinatorTest {

    @Test
    fun everyBackendOutcomeClearsTheLocalSessionAndRefreshCookie() {
        LogoutClientResult.entries.forEach { result ->
            val fixture = fixture(logoutClient = RecordingLogoutClient(result, null))

            fixture.coordinator.logout()

            assertNull(fixture.store.loadSession(), result.name)
            assertFalse(fixture.jar.hasAuthCookieFor(LOGOUT_URL), result.name)
        }
    }

    @Test
    fun repeatedLogoutIsLocallyIdempotentAndNeverRestoresCredentials() {
        val logoutClient = RecordingLogoutClient(LogoutClientResult.SUCCESS, null)
        val fixture = fixture(logoutClient = logoutClient)

        fixture.coordinator.logout()
        fixture.coordinator.logout()

        assertEquals(2, logoutClient.calls.get())
        assertNull(fixture.store.currentAccessToken())
        assertFalse(fixture.jar.hasAuthCookieFor(LOGOUT_URL))
    }

    @Test
    fun refreshInProgressThenLogoutUsesRotatedCookieAndEndsEmptyWithoutDeadlock() {
        val refreshStarted = CountDownLatch(1)
        val releaseRefresh = CountDownLatch(1)
        val logoutRequested = CountDownLatch(1)
        lateinit var jar: PersistentRefreshCookieJar
        val refreshClient = AuthRefreshClient {
            refreshStarted.countDown()
            assertTrue(releaseRefresh.await(5, TimeUnit.SECONDS))
            jar.saveFromResponse(REFRESH_URL, listOf(refreshCookie(COOKIE_B)))
            RefreshClientResult.Success(session(TOKEN_B))
        }
        val logoutClient = RecordingLogoutClient(LogoutClientResult.SUCCESS) {
            jar.loadForRequest(LOGOUT_URL).singleOrNull()?.value
        }
        val fixture = fixture(refreshClient, logoutClient)
        jar = fixture.jar
        val executor = Executors.newFixedThreadPool(2)

        try {
            val refresh = executor.submit<RefreshDecision> {
                fixture.coordinator.recoverFromUnauthorized(TOKEN_A)
            }
            assertTrue(refreshStarted.await(5, TimeUnit.SECONDS))
            val logout = executor.submit {
                logoutRequested.countDown()
                fixture.coordinator.logout()
            }
            assertTrue(logoutRequested.await(5, TimeUnit.SECONDS))

            releaseRefresh.countDown()
            assertEquals(TOKEN_B, (refresh.get(5, TimeUnit.SECONDS) as RefreshDecision.RetryWithToken).accessToken)
            logout.get(5, TimeUnit.SECONDS)

            assertEquals(COOKIE_B, logoutClient.cookieSeen)
            assertNull(fixture.store.loadSession())
            assertFalse(fixture.jar.hasAuthCookieFor(LOGOUT_URL))
        } finally {
            releaseRefresh.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun refreshAttemptWaitingAfterLogoutStartsCannotResurrectTheSession() {
        val logoutStarted = CountDownLatch(1)
        val releaseLogout = CountDownLatch(1)
        val refreshCalls = AtomicInteger()
        val refreshClient = AuthRefreshClient {
            refreshCalls.incrementAndGet()
            RefreshClientResult.Success(session(TOKEN_B))
        }
        val logoutClient = AuthLogoutClient {
            logoutStarted.countDown()
            assertTrue(releaseLogout.await(5, TimeUnit.SECONDS))
            LogoutClientResult.SUCCESS
        }
        val fixture = fixture(refreshClient, logoutClient)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val logout = executor.submit { fixture.coordinator.logout() }
            assertTrue(logoutStarted.await(5, TimeUnit.SECONDS))

            val refresh = executor.submit<RefreshDecision> {
                fixture.coordinator.recoverFromUnauthorized(TOKEN_A)
            }
            assertEquals(
                RefreshDecision.Unauthenticated,
                refresh.get(5, TimeUnit.SECONDS),
            )
            assertEquals(0, refreshCalls.get())

            releaseLogout.countDown()
            logout.get(5, TimeUnit.SECONDS)
            assertNull(fixture.store.currentAccessToken())
            assertFalse(fixture.jar.hasAuthCookieFor(LOGOUT_URL))
        } finally {
            releaseLogout.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun unexpectedLogoutClientExceptionStillClearsCredentialsAndReleasesLock() {
        val fixture = fixture(
            logoutClient = AuthLogoutClient { throw IllegalStateException("test failure") }
        )

        fixture.coordinator.logout()
        fixture.coordinator.logout()

        assertNull(fixture.store.currentAccessToken())
        assertFalse(fixture.jar.hasAuthCookieFor(LOGOUT_URL))
    }

    private fun fixture(
        refreshClient: AuthRefreshClient = AuthRefreshClient {
            RefreshClientResult.ServerFailure
        },
        logoutClient: AuthLogoutClient,
    ): Fixture {
        val store = AuthSessionStore(FakeSessionPersistence(), Gson())
        assertTrue(store.saveSession(session(TOKEN_A)))
        val jar = PersistentRefreshCookieJar(FakeCookiePersistence(), Gson()) { NOW }
        jar.saveFromResponse(LOGIN_URL, listOf(refreshCookie(COOKIE_A)))
        return Fixture(
            store,
            jar,
            AuthRefreshCoordinator(refreshClient, store, jar, logoutClient),
        )
    }

    private fun session(token: String): AuthenticatedSession {
        return AuthenticatedSession(
            accessToken = token,
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(12L, "person@example.com", AuthRole.USER),
        )
    }

    private fun refreshCookie(value: String): Cookie {
        return Cookie.Builder()
            .name(PersistentRefreshCookieJar.REFRESH_COOKIE_NAME)
            .value(value)
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
        val coordinator: AuthRefreshCoordinator,
    )

    private class RecordingLogoutClient(
        private val result: LogoutClientResult,
        private val cookieProvider: (() -> String?)?,
    ) : AuthLogoutClient {
        val calls = AtomicInteger()
        var cookieSeen: String? = null

        override fun logout(): LogoutClientResult {
            calls.incrementAndGet()
            cookieSeen = cookieProvider?.invoke()
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
        const val TOKEN_A = "test-access-token-A"
        const val TOKEN_B = "test-access-token-B"
        const val COOKIE_A = "test-refresh-cookie-A"
        const val COOKIE_B = "test-refresh-cookie-B"
        const val NOW = 1_800_000_000_000L
        val API_BASE_URL = "https://api.example.test/api/".toHttpUrl()
        val LOGIN_URL = requireNotNull(API_BASE_URL.resolve("auth/login"))
        val REFRESH_URL = requireNotNull(API_BASE_URL.resolve("auth/refresh"))
        val LOGOUT_URL = requireNotNull(API_BASE_URL.resolve("auth/logout"))
    }
}
