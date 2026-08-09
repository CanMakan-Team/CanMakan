package sg.edu.nus.iss.canmakan.features.auth.data

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Response

@DisplayName("UC19 7.2: Android server auth repository")
class ServerAuthRepositoryTest {

    @Test
    fun loginMapsValid200WithoutMutatingCredentials() = runTest {
        val api = FakeAuthApiService()
        val result = ServerAuthRepository(api).login(
            email = " Person@Example.COM ",
            password = "  Exact Password1!  ",
        )

        val session = successValue(result)
        assertEquals("access-token", session.accessToken)
        assertEquals("Bearer", session.tokenType)
        assertEquals(900, session.expiresIn)
        assertEquals(12L, session.user.userId)
        assertEquals(AuthRole.USER, session.user.role)
        assertEquals(
            LoginRequest(" Person@Example.COM ", "  Exact Password1!  "),
            api.lastLoginRequest,
        )
    }

    @Test
    fun loginAcceptsCaseInsensitiveBearerAndCanonicalizesIt() = runTest {
        val api = FakeAuthApiService(
            loginResponse = Response.success(validAuthResponse().copy(tokenType = "bearer"))
        )

        val session = successValue(ServerAuthRepository(api).login("person@example.com", "password"))

        assertEquals("Bearer", session.tokenType)
    }

    @Test
    fun loginMapsHttpFailuresDistinctly() = runTest {
        assertFailure(loginForStatus(400), AuthFailureType.MALFORMED_REQUEST)
        assertFailure(loginForStatus(401), AuthFailureType.INVALID_CREDENTIALS)
        assertFailure(loginForStatus(403), AuthFailureType.FORBIDDEN)
        assertFailure(loginForStatus(500), AuthFailureType.SERVER)
        assertFailure(loginForStatus(404), AuthFailureType.INVALID_RESPONSE)
    }

    @Test
    fun loginMapsIOExceptionToNetworkFailure() = runTest {
        val api = FakeAuthApiService(exception = IOException("password=do-not-expose"))

        assertFailure(
            ServerAuthRepository(api).login("person@example.com", "Password1!"),
            AuthFailureType.NETWORK,
        )
    }

    @Test
    fun loginRejectsEveryInvalidSuccessfulPayloadInvariant() = runTest {
        val valid = validAuthResponse()
        val invalidResponses = listOf(
            valid.copy(accessToken = ""),
            valid.copy(tokenType = "Basic"),
            valid.copy(expiresIn = 0),
            valid.copy(user = null),
            valid.copy(user = valid.user?.copy(userId = 0)),
            valid.copy(user = valid.user?.copy(email = "")),
            valid.copy(user = valid.user?.copy(role = null)),
        )

        invalidResponses.forEach { payload ->
            val api = FakeAuthApiService(loginResponse = Response.success(payload))
            assertFailure(
                ServerAuthRepository(api).login("person@example.com", "Password1!"),
                AuthFailureType.INVALID_RESPONSE,
            )
        }
    }

    @Test
    fun unexpectedConversionFailureBecomesInvalidResponse() = runTest {
        val api = FakeAuthApiService(exception = IllegalStateException("accessToken=do-not-expose"))

        assertFailure(
            ServerAuthRepository(api).login("person@example.com", "Password1!"),
            AuthFailureType.INVALID_RESPONSE,
        )
    }

    @Test
    fun meMapsValidUserAndAuthenticationFailures() = runTest {
        val user = successValue(ServerAuthRepository(FakeAuthApiService()).getCurrentUser())
        assertEquals(12L, user.userId)
        assertEquals("person@example.com", user.email)
        assertEquals(AuthRole.USER, user.role)

        val unauthorizedApi = FakeAuthApiService(currentUserResponse = errorResponse(401))
        assertFailure(
            ServerAuthRepository(unauthorizedApi).getCurrentUser(),
            AuthFailureType.UNAUTHENTICATED,
        )

        val forbiddenApi = FakeAuthApiService(currentUserResponse = errorResponse(403))
        assertFailure(
            ServerAuthRepository(forbiddenApi).getCurrentUser(),
            AuthFailureType.FORBIDDEN,
        )
    }

    @Test
    fun meRejectsInvalidPayloadAndMapsServerAndNetworkFailures() = runTest {
        val invalidApi = FakeAuthApiService(
            currentUserResponse = Response.success(
                AuthenticatedUserResponse(12L, "person@example.com", null)
            )
        )
        assertFailure(
            ServerAuthRepository(invalidApi).getCurrentUser(),
            AuthFailureType.INVALID_RESPONSE,
        )

        val serverApi = FakeAuthApiService(currentUserResponse = errorResponse(502))
        assertFailure(ServerAuthRepository(serverApi).getCurrentUser(), AuthFailureType.SERVER)

        val networkApi = FakeAuthApiService(exception = IOException("offline"))
        assertFailure(ServerAuthRepository(networkApi).getCurrentUser(), AuthFailureType.NETWORK)
    }

    private suspend fun loginForStatus(status: Int): AuthResult<AuthenticatedSession> {
        val api = FakeAuthApiService(loginResponse = errorResponse(status))
        return ServerAuthRepository(api).login("person@example.com", "Password1!")
    }

    private fun <T> successValue(result: AuthResult<T>): T {
        val success = assertInstanceOf(AuthResult.Success::class.java, result)
        @Suppress("UNCHECKED_CAST")
        return (success as AuthResult.Success<T>).value
    }

    private fun assertFailure(result: AuthResult<*>, expected: AuthFailureType) {
        val failure = assertInstanceOf(AuthResult.Failure::class.java, result)
        assertEquals(expected, failure.type)
    }

    private class FakeAuthApiService(
        private val loginResponse: Response<AuthResponse> = Response.success(validAuthResponse()),
        private val currentUserResponse: Response<AuthenticatedUserResponse> = Response.success(
            AuthenticatedUserResponse(12L, "person@example.com", AuthRole.USER)
        ),
        private val exception: Exception? = null,
    ) : AuthApiService {
        var lastLoginRequest: LoginRequest? = null

        override suspend fun login(request: LoginRequest): Response<AuthResponse> {
            lastLoginRequest = request
            exception?.let { throw it }
            return loginResponse
        }

        override suspend fun getCurrentUser(): Response<AuthenticatedUserResponse> {
            exception?.let { throw it }
            return currentUserResponse
        }
    }

    private companion object {
        fun <T> errorResponse(status: Int): Response<T> {
            return Response.error(
                status,
                "{\"message\":\"internal detail\"}"
                    .toResponseBody("application/json".toMediaType()),
            )
        }
    }
}
