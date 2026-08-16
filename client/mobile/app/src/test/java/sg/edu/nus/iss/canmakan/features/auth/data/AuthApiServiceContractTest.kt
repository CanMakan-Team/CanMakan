package sg.edu.nus.iss.canmakan.features.auth.data

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

@DisplayName("UC19 7.2: Android auth Retrofit contract")
class AuthApiServiceContractTest {

    @Test
    fun endpointDefinitionsMatchTheFrozenBackendContract() {
        // Bearer-client surface: login + /me + self-delete. Refresh/logout live on RefreshApiService.
        assertPost("login", "auth/login")
        assertEquals("auth/me", method("getCurrentUser").getAnnotation(GET::class.java).value)
        assertEquals("auth/account", method("deleteOwnAccount").getAnnotation(DELETE::class.java).value)

        assertTrue(hasParameterAnnotation(method("login"), Body::class.java))
        assertFalse(hasParameterAnnotation(method("getCurrentUser"), Body::class.java))
        assertFalse(hasParameterAnnotation(method("deleteOwnAccount"), Body::class.java))

        val methodNames = AuthApiService::class.java.declaredMethods.map { it.name }.toSet()
        assertFalse(methodNames.contains("refresh"))
        assertFalse(methodNames.contains("logout"))
    }

    @Test
    fun everyAuthEndpointDisablesTheSharedGeneralRetry() {
        assertEquals(
            listOf(NO_RETRY_HEADER, SESSION_REQUEST_HEADER),
            method("login").getAnnotation(Headers::class.java).value.toList(),
        )
        assertEquals(
            listOf(NO_RETRY_HEADER),
            method("getCurrentUser").getAnnotation(Headers::class.java).value.toList(),
        )
        assertEquals(
            listOf(NO_RETRY_HEADER, SESSION_REQUEST_HEADER),
            method("deleteOwnAccount").getAnnotation(Headers::class.java).value.toList(),
        )
    }

    @Test
    fun authEndpointsNeverDeclareXUserId() {
        listOf("login", "getCurrentUser", "deleteOwnAccount").forEach { name ->
            assertFalse(hasParameterAnnotation(method(name), Header::class.java), name)
            assertFalse(
                method(name).getAnnotation(Headers::class.java).value
                    .any { it.startsWith("X-User-Id", ignoreCase = true) },
                name,
            )
        }
    }

    @Test
    fun sensitiveModelsRedactCredentialsAndTokens() {
        val request = LoginRequest("person@example.com", "Exact Password1!")
        val response = validAuthResponse(accessToken = "secret-access-token")
        val session = AuthenticatedSession(
            accessToken = "secret-access-token",
            tokenType = "Bearer",
            expiresIn = 900,
            user = AuthenticatedUser(12L, "person@example.com", AuthRole.USER),
        )

        assertFalse(request.toString().contains("Exact Password1!"))
        assertFalse(response.toString().contains("secret-access-token"))
        assertFalse(session.toString().contains("secret-access-token"))
        assertFalse(AuthResult.Success(session).toString().contains("secret-access-token"))
    }

    @Test
    fun loginJsonContainsOnlyExactEmailAndPassword() {
        val request = LoginRequest(" Person@Example.COM ", "  Exact Password1!  ")

        assertEquals(
            "{\"email\":\" Person@Example.COM \",\"password\":\"  Exact Password1!  \"}",
            Gson().toJson(request),
        )
    }

    @Test
    fun unknownBackendRoleDeserializesToNullForRepositoryRejection() {
        assertEquals(AuthRole.USER, Gson().fromJson("\"USER\"", AuthRole::class.java))
        assertEquals(AuthRole.ADMIN, Gson().fromJson("\"ADMIN\"", AuthRole::class.java))
        assertNull(Gson().fromJson("\"ROLE_FAMILY_ADMIN\"", AuthRole::class.java))
    }

    private fun assertPost(name: String, expectedPath: String) {
        assertEquals(expectedPath, method(name).getAnnotation(POST::class.java).value)
    }

    private fun method(name: String) = AuthApiService::class.java.declaredMethods.single {
        it.name == name
    }

    private fun hasParameterAnnotation(
        method: java.lang.reflect.Method,
        annotationType: Class<out Annotation>,
    ): Boolean {
        return method.parameterAnnotations.flatten().any { annotationType.isInstance(it) }
    }

    private companion object {
        const val NO_RETRY_HEADER = "X-CanMakan-No-Retry: true"
        const val SESSION_REQUEST_HEADER = "X-CanMakan-Session-Request: 1"
    }
}

internal fun validAuthResponse(accessToken: String = "access-token"): AuthResponse {
    return AuthResponse(
        accessToken = accessToken,
        tokenType = "Bearer",
        expiresIn = 900,
        user = AuthenticatedUserResponse(12L, "person@example.com", AuthRole.USER),
    )
}
