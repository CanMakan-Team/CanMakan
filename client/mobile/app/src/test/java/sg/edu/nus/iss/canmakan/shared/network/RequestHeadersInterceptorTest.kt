package sg.edu.nus.iss.canmakan.shared.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Request headers interceptor")
class RequestHeadersInterceptorTest {

    @Test
    fun debugAddsNgrokSkipHeaderAndHonestUserAgent() {
        val captured = capture(
            RequestHeadersInterceptor(
                includeNgrokSkipHeader = true,
                userAgent = "CanMakan-Android/1.0",
            ),
        )
        assertEquals("CanMakan-Android/1.0", captured.header("User-Agent"))
        assertEquals("true", captured.header("ngrok-skip-browser-warning"))
    }

    @Test
    fun releaseOmitsNgrokHeader() {
        val captured = capture(
            RequestHeadersInterceptor(
                includeNgrokSkipHeader = false,
                userAgent = "CanMakan-Android/1.0",
            ),
        )
        assertEquals("CanMakan-Android/1.0", captured.header("User-Agent"))
        assertNull(captured.header("ngrok-skip-browser-warning"))
    }

    private fun capture(interceptor: RequestHeadersInterceptor): Request {
        var captured: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                captured = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody(JSON))
                    .build()
            }
            .build()
        client.newCall(Request.Builder().url("https://api.example.test/api/scan").get().build())
            .execute()
            .close()
        return requireNotNull(captured)
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
