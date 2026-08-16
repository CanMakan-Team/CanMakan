package sg.edu.nus.iss.canmakan.features.auth.session

import com.google.gson.Gson
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
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
import sg.edu.nus.iss.canmakan.shared.di.NetworkModule

@DisplayName("UC19 7.5: origin-safe Bearer interceptor")
class BearerAuthInterceptorTest {
    private lateinit var persistence: FakeAuthSessionPersistence
    private lateinit var sessionStore: AuthSessionStore
    private lateinit var interceptor: BearerAuthInterceptor

    @BeforeEach
    fun setUp() {
        persistence = FakeAuthSessionPersistence()
        sessionStore = AuthSessionStore(persistence, Gson())
        interceptor = BearerAuthInterceptor(sessionStore, API_BASE_URL)
    }

    @Test
    fun noSessionProceedsWithoutFabricatingAuthorization() {
        val captured = executeAndCapture(protectedRequest())

        assertNull(captured.header(AUTHORIZATION))
        assertTrue(captured.headers.values(AUTHORIZATION).isEmpty())
    }

    @Test
    fun meReceivesExactlyOneBearerHeaderFromTheCurrentSession() {
        saveToken(TEST_TOKEN_A)

        val captured = executeAndCapture(protectedRequest("auth/me"))

        assertEquals("Bearer $TEST_TOKEN_A", captured.header(AUTHORIZATION))
        assertEquals(1, captured.headers.values(AUTHORIZATION).size)
    }

    @Test
    fun scanValidationOnTheConfiguredOriginReceivesAuthenticationContext() {
        saveToken(TEST_TOKEN_A)
        val scanRequest = Request.Builder()
            .url(requireNotNull(API_BASE_URL.resolve("scan/validate")))
            .post("{\"barcode\":\"3017620422003\"}".toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val captured = executeAndCapture(scanRequest)

        assertEquals("Bearer $TEST_TOKEN_A", captured.header(AUTHORIZATION))
    }

    @Test
    fun tokenReplacementIsReadFromTheStoreForTheNextRequest() {
        saveToken(TEST_TOKEN_A)
        assertEquals(
            "Bearer $TEST_TOKEN_A",
            executeAndCapture(protectedRequest()).header(AUTHORIZATION),
        )

        saveToken(TEST_TOKEN_B)

        assertEquals(
            "Bearer $TEST_TOKEN_B",
            executeAndCapture(protectedRequest()).header(AUTHORIZATION),
        )
    }

    @Test
    fun clearingTheSessionRemovesAuthorizationFromTheNextRequest() {
        saveToken(TEST_TOKEN_A)
        assertTrue(executeAndCapture(protectedRequest()).header(AUTHORIZATION) != null)

        sessionStore.clearSession()

        assertNull(executeAndCapture(protectedRequest()).header(AUTHORIZATION))
    }

    @Test
    fun wrongHostSchemeAndPortNeverReceiveTheStoredToken() {
        saveToken(TEST_TOKEN_A)
        val foreignOrigins = listOf(
            "https://foreign.example.test:8443/api/auth/me",
            "http://api.example.test:8443/api/auth/me",
            "https://api.example.test:9443/api/auth/me",
        )

        foreignOrigins.forEach { url ->
            val captured = executeAndCapture(Request.Builder().url(url).get().build())
            assertNull(captured.header(AUTHORIZATION), url)
        }
    }

    @Test
    fun exactPublicAuthEndpointsAndHealthRemainTokenIndependent() {
        saveToken(TEST_TOKEN_A)
        listOf("auth/register", "auth/login", "auth/refresh", "auth/logout").forEach { route ->
            val request = Request.Builder()
                .url(requireNotNull(API_BASE_URL.resolve(route)))
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()

            assertNull(executeAndCapture(request).header(AUTHORIZATION), route)
        }

        val healthRequest = Request.Builder()
            .url("https://api.example.test:8443/actuator/health")
            .get()
            .build()
        assertNull(executeAndCapture(healthRequest).header(AUTHORIZATION))
    }

    @Test
    fun explicitAuthorizationIsPreservedWithoutAddingAnotherHeader() {
        saveToken(TEST_TOKEN_A)
        val request = protectedRequest().newBuilder()
            .header(AUTHORIZATION, EXPLICIT_AUTHORIZATION)
            .build()

        val captured = executeAndCapture(request)

        assertEquals(EXPLICIT_AUTHORIZATION, captured.header(AUTHORIZATION))
        assertEquals(1, captured.headers.values(AUTHORIZATION).size)
    }

    @Test
    fun networkModuleInstallsTheInterceptorAndStringRepresentationRedactsToken() {
        saveToken(TEST_TOKEN_A)
        val client = NetworkModule.provideOkHttpClient(
            loggingInterceptor = NetworkModule.provideLoggingInterceptor(),
            cookieJar = CookieJar.NO_COOKIES,
            bearerAuthInterceptor = interceptor,
        )

        assertTrue(client.interceptors.contains(interceptor))
        assertEquals(HttpLoggingInterceptor.Level.BASIC, NetworkModule.provideLoggingInterceptor().level)
        assertFalse(interceptor.toString().contains(TEST_TOKEN_A))
        assertFalse(interceptor.toString().contains(sessionStore.toString()))
    }

    private fun protectedRequest(route: String = "auth/me"): Request {
        return Request.Builder()
            .url(requireNotNull(API_BASE_URL.resolve(route)))
            .get()
            .build()
    }

    private fun executeAndCapture(request: Request): Request {
        var capturedRequest: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                capturedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody(JSON_MEDIA_TYPE))
                    .build()
            }
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
        }
        return requireNotNull(capturedRequest)
    }

    private fun saveToken(token: String) {
        assertTrue(
            sessionStore.saveSession(
                AuthenticatedSession(
                    accessToken = token,
                    tokenType = "Bearer",
                    expiresIn = 900,
                    user = AuthenticatedUser(12L, "person@example.com", AuthRole.USER),
                )
            )
        )
    }

    private class FakeAuthSessionPersistence : AuthSessionPersistence {
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

    private companion object {
        val API_BASE_URL: HttpUrl = "https://api.example.test:8443/api/".toHttpUrl()
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val AUTHORIZATION = "Authorization"
        const val TEST_TOKEN_A = "test-access-token-A"
        const val TEST_TOKEN_B = "test-access-token-B"
        const val EXPLICIT_AUTHORIZATION = "Basic explicit-test-credential"
    }
}
