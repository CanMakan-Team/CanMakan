package sg.edu.nus.iss.canmakan.shared.network

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RetryPolicyInterceptorTest {

    @Test
    fun clientErrorsAreNotRetried() {
        val result = execute(listOf(400, 200))
        assertEquals(400, result.code)
        assertEquals(1, result.calls)
    }

    @Test
    fun serverErrorsRetryUntilSuccess() {
        val result = execute(listOf(503, 200))
        assertEquals(200, result.code)
        assertEquals(2, result.calls)
    }

    @Test
    fun noRetryHeaderSkipsRetriesAndIsStripped() {
        var seenNoRetryHeader = false
        val client = OkHttpClient.Builder()
            .addInterceptor(RetryPolicyInterceptor())
            .addInterceptor { chain ->
                seenNoRetryHeader = chain.request().header(RetryPolicyInterceptor.NO_RETRY_HEADER) != null
                response(chain.request(), 503)
            }
            .build()

        val code = client.newCall(
            Request.Builder()
                .url("https://api.example.test/health")
                .header(RetryPolicyInterceptor.NO_RETRY_HEADER, "true")
                .build(),
        ).execute().use { it.code }

        assertEquals(503, code)
        assertEquals(false, seenNoRetryHeader)
    }

    @Test
    fun ioExceptionsRetryThenRethrowTheLastFailure() {
        var calls = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(RetryPolicyInterceptor())
            .addInterceptor { chain ->
                calls++
                throw IOException("offline-$calls")
            }
            .build()

        val error = assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url("https://api.example.test/health").build()).execute()
        }
        assertEquals("offline-3", error.message)
        assertEquals(3, calls)
    }

    @Test
    fun responseAfterPriorAuthChallengeIsNotRetried() {
        var calls = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(RetryPolicyInterceptor())
            .addInterceptor { chain ->
                calls++
                val prior = response(chain.request(), 401)
                response(chain.request(), 503).newBuilder().priorResponse(prior).build()
            }
            .build()

        val code = client.newCall(
            Request.Builder().url("https://api.example.test/health").build(),
        ).execute().use { it.code }

        assertEquals(503, code)
        assertEquals(1, calls)
    }

    private fun execute(codes: List<Int>): CallResult {
        val remaining = ArrayDeque(codes)
        var calls = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(RetryPolicyInterceptor())
            .addInterceptor { chain ->
                calls++
                response(chain.request(), remaining.removeFirst())
            }
            .build()
        val code = client.newCall(
            Request.Builder().url("https://api.example.test/health").build(),
        ).execute().use { it.code }
        return CallResult(code, calls)
    }

    private fun response(request: Request, code: Int): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("status $code")
            .body("{}".toResponseBody())
            .build()
    }

    private data class CallResult(val code: Int, val calls: Int)
}
