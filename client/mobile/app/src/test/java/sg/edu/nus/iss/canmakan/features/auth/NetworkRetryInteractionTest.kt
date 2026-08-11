package sg.edu.nus.iss.canmakan.features.auth

import java.util.ArrayDeque
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.shared.di.NetworkModule

@DisplayName("UC19 7.6: generic retry and authentication interaction")
class NetworkRetryInteractionTest {

    @Test
    fun clientErrorsIncludingUnauthorizedAndForbiddenAreNeverGenericallyRetried() {
        listOf(400, 401, 403, 404).forEach { status ->
            val result = execute(ArrayDeque(listOf(status)))

            assertEquals(status, result.status)
            assertEquals(1, result.calls)
        }
    }

    @Test
    fun transientServerFailureStillUsesTheExistingRetryBehavior() {
        val result = execute(ArrayDeque(listOf(503, 200)))

        assertEquals(200, result.status)
        assertEquals(2, result.calls)
    }

    @Test
    fun responseAfterAnAuthenticationFollowUpIsNeverGenericallyRetried() {
        var calls = 0
        val client = NetworkModule.provideOkHttpClient(
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE }
        ).newBuilder()
            .addInterceptor { chain ->
                calls++
                val prior = Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .build()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Server unavailable after auth follow-up")
                    .priorResponse(prior)
                    .body("{}".toResponseBody(JSON_MEDIA_TYPE))
                    .build()
            }
            .build()
        val request = Request.Builder()
            .url("https://api.example.test/api/protected")
            .get()
            .build()

        val status = client.newCall(request).execute().use { it.code }

        assertEquals(503, status)
        assertEquals(1, calls)
    }

    private fun execute(statuses: ArrayDeque<Int>): CallResult {
        var calls = 0
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
        val client = NetworkModule.provideOkHttpClient(logging)
            .newBuilder()
            .addInterceptor { chain ->
                calls++
                val code = statuses.removeFirst()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Test response")
                    .body("{}".toResponseBody(JSON_MEDIA_TYPE))
                    .build()
            }
            .build()
        val request = Request.Builder()
            .url("https://api.example.test/api/protected")
            .get()
            .build()

        val status = client.newCall(request).execute().use { it.code }
        return CallResult(status, calls)
    }

    private data class CallResult(
        val status: Int,
        val calls: Int,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
