package sg.edu.nus.iss.canmakan.features.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.shared.di.NetworkModule

@DisplayName("UC18: Android registration network security")
class RegistrationNetworkSecurityTest {

    @Test
    @DisplayName("UC18 N1: shared client never logs registration request bodies and never retries them")
    fun registrationBodyIsNotLoggedAndRequestIsNotRetried() {
        assertEquals(
            HttpLoggingInterceptor.Level.BASIC,
            NetworkModule.provideLoggingInterceptor().level,
        )
        val logs = mutableListOf<String>()
        val loggingInterceptor = HttpLoggingInterceptor { logs += it }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        var networkCalls = 0
        var internalHeaderAtNetwork: String? = "not-called"
        val client = NetworkModule.provideOkHttpClient(loggingInterceptor)
            .newBuilder()
            .addInterceptor { chain ->
                networkCalls++
                internalHeaderAtNetwork = chain.request().header("X-CanMakan-No-Retry")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Server error")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val request = Request.Builder()
            .url("http://localhost/api/auth/register")
            .header("X-CanMakan-No-Retry", "true")
            .post(
                "{\"email\":\"person@example.com\",\"password\":\"Password1!\"}"
                    .toRequestBody("application/json".toMediaType())
            )
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(500, response.code)
        }

        assertEquals(1, networkCalls)
        assertEquals(null, internalHeaderAtNetwork)
        assertFalse(logs.joinToString("\n").contains("Password1!"))
        assertFalse(logs.joinToString("\n").contains("person@example.com"))
    }
}
