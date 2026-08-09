package sg.edu.nus.iss.canmakan.features.auth.data

import com.google.gson.Gson
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import sg.edu.nus.iss.canmakan.features.auth.session.AuthRefreshClient
import sg.edu.nus.iss.canmakan.features.auth.session.AuthRefreshCoordinator
import sg.edu.nus.iss.canmakan.features.auth.session.AuthRequestPolicy
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionPersistence
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore
import sg.edu.nus.iss.canmakan.features.auth.session.BearerAuthInterceptor
import sg.edu.nus.iss.canmakan.features.auth.session.BearerAuthenticator
import sg.edu.nus.iss.canmakan.features.auth.session.PersistentRefreshCookieJar
import sg.edu.nus.iss.canmakan.features.auth.session.RefreshClientResult
import sg.edu.nus.iss.canmakan.features.auth.session.RefreshCookiePersistence
import sg.edu.nus.iss.canmakan.features.auth.session.RetrofitAuthRefreshClient
import sg.edu.nus.iss.canmakan.shared.di.NetworkModule

@DisplayName("UC19 7.6: dedicated non-recursive refresh network")
class DedicatedRefreshNetworkTest {

    @Test
    fun refreshContractHasNoBodyOrInternalControlHeader() {
        val method = RefreshApiService::class.java.getDeclaredMethod("refresh")

        assertEquals(
            "auth/refresh",
            requireNotNull(method.getAnnotation(POST::class.java)).value,
        )
        assertNull(method.getAnnotation(Headers::class.java))
        assertFalse(method.parameterAnnotations.flatten().any { it is Body })
        assertEquals(0, method.parameterCount)
    }

    @Test
    fun validResponseUsesOneHeaderSafeRequestAndRetainsFrozenValidation() {
        val capture = NetworkCapture(
            code = 200,
            body = """
                {
                  "accessToken":"test-access-token-B",
                  "tokenType":"Bearer",
                  "expiresIn":900,
                  "user":{"userId":12,"email":"person@example.com","role":"USER"}
                }
            """.trimIndent(),
        )

        val result = execute(capture)

        val success = assertInstanceOf(RefreshClientResult.Success::class.java, result)
        assertEquals("test-access-token-B", success.session.accessToken)
        assertEquals(1, capture.calls.get())
        assertEquals("POST", capture.method)
        assertEquals("/api/auth/refresh", capture.path)
        assertNull(capture.authorization)
        assertNull(capture.internalNoRetryHeader)
        assertFalse(success.toString().contains("test-access-token-B"))
    }

    @Test
    fun refreshHttpAndPayloadFailuresRemainDistinctAndNeverRetryTheDedicatedCall() {
        listOf(
            Triple(401, "{}", RefreshClientResult.Unauthenticated),
            Triple(403, "{}", RefreshClientResult.Forbidden),
            Triple(503, "{}", RefreshClientResult.ServerFailure),
            Triple(200, "{\"accessToken\":\"\",\"tokenType\":\"Bearer\"}", RefreshClientResult.InvalidResponse),
        ).forEach { (code, body, expected) ->
            val capture = NetworkCapture(code = code, body = body)

            assertEquals(expected, execute(capture))
            assertEquals(1, capture.calls.get(), code.toString())
        }
    }

    @Test
    fun refreshIOExceptionIsNetworkFailureWithoutAnInternalRetry() {
        val capture = NetworkCapture(exception = IOException("offline"))

        assertEquals(RefreshClientResult.NetworkFailure, execute(capture))
        assertEquals(1, capture.calls.get())
    }

    @Test
    fun mainAndRefreshClientsShareTheSameJarButOnlyMainHasBearerAndAuthenticator() {
        val jar = refreshCookieJar()
        val store = AuthSessionStore(FakeSessionPersistence(), Gson())
        val policy = AuthRequestPolicy(API_BASE_URL)
        val bearer = BearerAuthInterceptor(store, policy)
        val coordinator = AuthRefreshCoordinator(
            AuthRefreshClient { RefreshClientResult.ServerFailure },
            store,
            jar,
        )
        val authenticator = BearerAuthenticator(policy, coordinator)
        val refreshClient = NetworkModule.provideAuthRefreshOkHttpClient(jar)
        val mainClient = NetworkModule.provideOkHttpClient(
            loggingInterceptor = NetworkModule.provideLoggingInterceptor(),
            cookieJar = jar,
            bearerAuthInterceptor = bearer,
            bearerAuthenticator = authenticator,
        )

        assertSame(jar, refreshClient.cookieJar)
        assertSame(jar, mainClient.cookieJar)
        assertSame(Authenticator.NONE, refreshClient.authenticator)
        assertSame(authenticator, mainClient.authenticator)
        assertFalse(refreshClient.retryOnConnectionFailure)
        assertTrue(refreshClient.interceptors.isEmpty())
        assertTrue(mainClient.interceptors.contains(bearer))
    }

    private fun execute(capture: NetworkCapture): RefreshClientResult {
        val baseClient = NetworkModule.provideAuthRefreshOkHttpClient(refreshCookieJar())
        val testClient = baseClient.newBuilder()
            .addInterceptor { chain ->
                capture.calls.incrementAndGet()
                val request = chain.request()
                capture.method = request.method
                capture.path = request.url.encodedPath
                capture.authorization = request.header("Authorization")
                capture.internalNoRetryHeader = request.header("X-CanMakan-No-Retry")
                capture.exception?.let { throw it }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(capture.code)
                    .message("Test response")
                    .body(capture.body.toResponseBody(JSON_MEDIA_TYPE))
                    .build()
            }
            .build()
        val retrofit = NetworkModule.provideAuthRefreshRetrofit(
            refreshOkHttpClient = testClient,
            gson = Gson(),
            apiBaseUrl = API_BASE_URL,
        )
        val service = NetworkModule.provideRefreshApiService(retrofit)
        return RetrofitAuthRefreshClient(service).refresh()
    }

    private fun refreshCookieJar(): PersistentRefreshCookieJar {
        return PersistentRefreshCookieJar(FakeCookiePersistence(), Gson()) { NOW }
    }

    private data class NetworkCapture(
        val code: Int = 200,
        val body: String = "{}",
        val exception: IOException? = null,
        val calls: AtomicInteger = AtomicInteger(),
        var method: String? = null,
        var path: String? = null,
        var authorization: String? = null,
        var internalNoRetryHeader: String? = null,
    )

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
        const val NOW = 1_800_000_000_000L
        val API_BASE_URL = "https://api.example.test/api/".toHttpUrl()
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
