package sg.edu.nus.iss.canmakan.features.auth.session

import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedSession
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class AuthLogoutActionTest {

    @Test
    fun serializedLogoutClearsSessionAndCookieOnIoDispatcher() = runTest {
        val store = AuthSessionStore(MemorySessionPersistence(), Gson())
        assertTrue(store.saveSession(session()))
        val jar = PersistentRefreshCookieJar(MemoryCookiePersistence(), Gson()) { NOW }
        jar.saveFromResponse(LOGIN_URL, listOf(refreshCookie()))
        val logoutClient = CountingLogoutClient()
        val coordinator = AuthRefreshCoordinator(
            refreshClient = AuthRefreshClient { RefreshClientResult.ServerFailure },
            authSessionStore = store,
            refreshCookieJar = jar,
            logoutClient = logoutClient,
        )
        val action = SerializedAuthLogoutAction(coordinator, UnconfinedTestDispatcher(testScheduler))

        action.logout()

        assertEquals(1, logoutClient.calls.get())
        assertNull(store.loadSession())
        assertFalse(jar.hasAuthCookieFor(LOGOUT_URL))
        assertEquals("SerializedAuthLogoutAction(state=<redacted>)", action.toString())
    }

    private fun session(): AuthenticatedSession {
        return AuthenticatedSession(
            accessToken = "access-token",
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(12L, "person@example.com", AuthRole.USER),
        )
    }

    private fun refreshCookie(): Cookie {
        return Cookie.Builder()
            .name(PersistentRefreshCookieJar.REFRESH_COOKIE_NAME)
            .value("refresh-cookie")
            .hostOnlyDomain("api.example.test")
            .path("/api/auth")
            .expiresAt(NOW + 60_000)
            .secure()
            .httpOnly()
            .build()
    }

    private class CountingLogoutClient : AuthLogoutClient {
        val calls = AtomicInteger()
        override fun logout(): LogoutClientResult {
            calls.incrementAndGet()
            return LogoutClientResult.SUCCESS
        }
    }

    private class MemorySessionPersistence : AuthSessionPersistence {
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

    private class MemoryCookiePersistence : RefreshCookiePersistence {
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
        const val NOW = 1_800_000_000_000L
        val API_BASE_URL = "https://api.example.test/api/".toHttpUrl()
        val LOGIN_URL = requireNotNull(API_BASE_URL.resolve("auth/login"))
        val LOGOUT_URL = requireNotNull(API_BASE_URL.resolve("auth/logout"))
    }
}
