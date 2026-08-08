package sg.edu.nus.iss.canmakan.features.auth.session

import com.google.gson.Gson
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("UC19 7.3: encrypted persistent refresh CookieJar")
class PersistentRefreshCookieJarTest {
    private var now = 1_800_000_000_000L

    @Test
    fun matchingUsesOkHttpHostPathAndSecureSemantics() {
        val jar = newJar()
        jar.saveFromResponse(HTTPS_LOGIN_URL, listOf(refreshCookie(TEST_REFRESH_A)))

        val httpsCookie = jar.loadForRequest(HTTPS_REFRESH_URL).single()
        assertEquals(TEST_REFRESH_A, httpsCookie.value)
        assertTrue(httpsCookie.httpOnly)
        assertTrue(httpsCookie.secure)
        assertTrue(httpsCookie.hostOnly)
        assertEquals("Strict", httpsCookie.sameSite)
        assertTrue(jar.loadForRequest("https://api.example.test/api/products".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://other.example.test/api/auth/refresh".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://api.example.test/api/auth/refresh".toHttpUrl()).isEmpty())
    }

    @Test
    fun hostBoundCookieIsNotRewrittenBetweenEmulatorLocalhostOrLanHosts() {
        val jar = newJar()
        val emulatorLogin = "https://10.0.2.2/api/auth/login".toHttpUrl()
        jar.saveFromResponse(
            emulatorLogin,
            listOf(refreshCookie(TEST_REFRESH_A, domain = "10.0.2.2")),
        )

        assertEquals(
            1,
            jar.loadForRequest("https://10.0.2.2/api/auth/refresh".toHttpUrl()).size,
        )
        assertTrue(jar.loadForRequest("https://localhost/api/auth/refresh".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://192.168.1.50/api/auth/refresh".toHttpUrl()).isEmpty())
    }

    @Test
    fun expiredCookieIsRemovedFromMemoryAndPersistence() {
        val persistence = FakeRefreshCookiePersistence()
        val jar = newJar(persistence)
        jar.saveFromResponse(HTTPS_LOGIN_URL, listOf(refreshCookie(TEST_REFRESH_A)))
        assertTrue(persistence.serializedCookies != null)

        now += COOKIE_LIFETIME_MILLIS + 1

        assertTrue(jar.loadForRequest(HTTPS_REFRESH_URL).isEmpty())
        assertNull(persistence.serializedCookies)
    }

    @Test
    fun rotatedCookieReplacesTheSameNameDomainAndPathIdentity() {
        val persistence = FakeRefreshCookiePersistence()
        val jar = newJar(persistence)
        jar.saveFromResponse(HTTPS_LOGIN_URL, listOf(refreshCookie(TEST_REFRESH_A)))
        assertEquals(TEST_REFRESH_A, jar.loadForRequest(HTTPS_REFRESH_URL).single().value)

        jar.saveFromResponse(HTTPS_REFRESH_URL, listOf(refreshCookie(TEST_REFRESH_B)))

        val activeCookies = jar.loadForRequest(HTTPS_REFRESH_URL)
        assertEquals(1, activeCookies.size)
        assertEquals(TEST_REFRESH_B, activeCookies.single().value)
        assertFalse(requireNotNull(persistence.serializedCookies).contains(TEST_REFRESH_A))
        assertFalse(requireNotNull(persistence.serializedCookies).contains(TEST_REFRESH_B))
        assertEquals(
            TEST_REFRESH_B,
            newJar(persistence).loadForRequest(HTTPS_REFRESH_URL).single().value,
        )
    }

    @Test
    fun backendStyleMaxAgeZeroCookieDeletesMemoryAndPersistedState() {
        val persistence = FakeRefreshCookiePersistence()
        val jar = newJar(persistence)
        jar.saveFromResponse(HTTPS_LOGIN_URL, listOf(refreshCookie(TEST_REFRESH_A)))
        val deletionCookie = requireNotNull(
            Cookie.parse(
                HTTPS_REFRESH_URL,
                "canmakan_refresh=; Max-Age=0; Path=/api/auth; Secure; HttpOnly; SameSite=Strict",
            )
        )

        jar.saveFromResponse(HTTPS_REFRESH_URL, listOf(deletionCookie))

        assertTrue(jar.loadForRequest(HTTPS_REFRESH_URL).isEmpty())
        assertNull(persistence.serializedCookies)
        assertTrue(newJar(persistence).loadForRequest(HTTPS_REFRESH_URL).isEmpty())
    }

    @Test
    fun persistentCookieSurvivesARecreatedJarWithAllSupportedAttributes() {
        val persistence = FakeRefreshCookiePersistence()
        newJar(persistence).saveFromResponse(
            HTTPS_LOGIN_URL,
            listOf(refreshCookie(TEST_REFRESH_A)),
        )

        val restored = newJar(persistence).loadForRequest(HTTPS_REFRESH_URL).single()

        assertEquals(TEST_REFRESH_A, restored.value)
        assertEquals("api.example.test", restored.domain)
        assertEquals("/api/auth", restored.path)
        assertTrue(restored.persistent)
        assertTrue(restored.secure)
        assertTrue(restored.httpOnly)
        assertTrue(restored.hostOnly)
        assertEquals("Strict", restored.sameSite)
    }

    @Test
    fun explicitClearRemovesCookieFromMemoryAndPersistence() {
        val persistence = FakeRefreshCookiePersistence()
        val jar = newJar(persistence)
        jar.saveFromResponse(HTTPS_LOGIN_URL, listOf(refreshCookie(TEST_REFRESH_A)))

        assertTrue(jar.clearAuthCookies())

        assertTrue(jar.loadForRequest(HTTPS_REFRESH_URL).isEmpty())
        assertNull(persistence.serializedCookies)
    }

    @Test
    fun unrelatedCookiesAreIgnoredByTheLeastPrivilegeJar() {
        val persistence = FakeRefreshCookiePersistence()
        val jar = newJar(persistence)
        val unrelated = Cookie.Builder()
            .name("analytics_session")
            .value("irrelevant-test-value")
            .hostOnlyDomain("api.example.test")
            .path("/")
            .expiresAt(now + COOKIE_LIFETIME_MILLIS)
            .build()

        jar.saveFromResponse(HTTPS_LOGIN_URL, listOf(unrelated))

        assertTrue(jar.loadForRequest(HTTPS_LOGIN_URL).isEmpty())
        assertNull(persistence.serializedCookies)
    }

    @Test
    fun sameCookieNameWithDifferentPathsRetainsDistinctIdentities() {
        val jar = newJar()
        jar.saveFromResponse(
            HTTPS_LOGIN_URL,
            listOf(
                refreshCookie(TEST_REFRESH_A, path = "/api/auth"),
                refreshCookie(TEST_REFRESH_B, path = "/api/auth/special"),
            ),
        )

        assertEquals(1, jar.loadForRequest(HTTPS_REFRESH_URL).size)
        assertEquals(
            2,
            jar.loadForRequest("https://api.example.test/api/auth/special/refresh".toHttpUrl()).size,
        )
    }

    @Test
    fun corruptCookieRecordIsDiscardedWithoutLosingAnotherValidRecord() {
        val persistence = FakeRefreshCookiePersistence()
        newJar(persistence).saveFromResponse(HTTPS_LOGIN_URL, listOf(refreshCookie(TEST_REFRESH_A)))
        persistence.serializedCookies = requireNotNull(persistence.serializedCookies)
            .dropLast(1) + ",{\"name\":\"canmakan_refresh\"}]"

        val restoredJar = newJar(persistence)

        assertEquals(TEST_REFRESH_A, restoredJar.loadForRequest(HTTPS_REFRESH_URL).single().value)
        assertFalse(requireNotNull(persistence.serializedCookies).contains(TEST_REFRESH_A))
    }

    @Test
    fun jarStringRepresentationNeverContainsCookieValue() {
        val jar = newJar()
        jar.saveFromResponse(HTTPS_LOGIN_URL, listOf(refreshCookie(TEST_REFRESH_A)))

        assertFalse(jar.toString().contains(TEST_REFRESH_A))
    }

    private fun newJar(
        persistence: FakeRefreshCookiePersistence = FakeRefreshCookiePersistence(),
    ): PersistentRefreshCookieJar {
        return PersistentRefreshCookieJar(persistence, Gson()) { now }
    }

    private fun refreshCookie(
        value: String,
        domain: String = "api.example.test",
        path: String = "/api/auth",
    ): Cookie {
        return Cookie.Builder()
            .name(PersistentRefreshCookieJar.REFRESH_COOKIE_NAME)
            .value(value)
            .hostOnlyDomain(domain)
            .path(path)
            .expiresAt(now + COOKIE_LIFETIME_MILLIS)
            .secure()
            .httpOnly()
            .sameSite("Strict")
            .build()
    }

    private class FakeRefreshCookiePersistence(
        var serializedCookies: String? = null,
    ) : RefreshCookiePersistence {
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
        val HTTPS_LOGIN_URL: HttpUrl = "https://api.example.test/api/auth/login".toHttpUrl()
        val HTTPS_REFRESH_URL: HttpUrl = "https://api.example.test/api/auth/refresh".toHttpUrl()
        const val COOKIE_LIFETIME_MILLIS = 60_000L
        const val TEST_REFRESH_A = "test-refresh-A"
        const val TEST_REFRESH_B = "test-refresh-B"
    }
}
