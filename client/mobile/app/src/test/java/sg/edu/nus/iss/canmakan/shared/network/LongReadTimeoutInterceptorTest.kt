package sg.edu.nus.iss.canmakan.shared.network

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LongReadTimeoutInterceptorTest {

    @Test
    fun assessAndRecommendationsUseTheLongReadTimeout() {
        assertEquals(
            LongReadTimeoutInterceptor.LONG_READ_TIMEOUT_SECONDS * 1000,
            timeoutMs("https://api.example.test/api/scan/assess"),
        )
        assertEquals(
            LongReadTimeoutInterceptor.LONG_READ_TIMEOUT_SECONDS * 1000,
            timeoutMs("https://api.example.test/api/profiles/9/recommendations"),
        )
    }

    @Test
    fun otherPathsKeepTheChainTimeout() {
        assertEquals(15_000, timeoutMs("https://api.example.test/api/scan/validate"))
    }

    private fun timeoutMs(url: String): Int {
        var observed = -1
        val client = OkHttpClient.Builder()
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(LongReadTimeoutInterceptor())
            .addInterceptor { chain ->
                observed = chain.readTimeoutMillis()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("ok")
                    .body("{}".toResponseBody())
                    .build()
            }
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().close()
        return observed
    }
}
