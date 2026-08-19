package sg.edu.nus.iss.canmakan.features.auth.data

import com.google.gson.Gson
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Response

@DisplayName("UC18: Android registration repository")
class ServerRegistrationRepositoryTest {

    @Test
    @DisplayName("UC18 A1: request JSON contains only account fields and redacts its string form")
    fun requestContainsOnlyAccountFieldsAndRedactsPassword() {
        val request = RegistrationRequest("person@example.com", "Password1!")

        assertEquals(
            "{\"email\":\"person@example.com\",\"password\":\"Password1!\"}",
            Gson().toJson(RegistrationRequest("person@example.com", "Password1!")),
        )
        assertFalse(request.toString().contains("Password1!"))
    }

    @Test
    @DisplayName("UC18 A2: account-only 201 response becomes success")
    fun createdResponseBecomesSuccess() = kotlinx.coroutines.test.runTest {
        val api = FakeRegistrationApiService(
            response = Response.success(
                201,
                RegistrationResponse(14L, "person@example.com", true),
            )
        )

        val result = ServerRegistrationRepository(api).register(
            "person@example.com",
            "Password1!",
            null,
        )

        val success = assertInstanceOf(RegistrationResult.Success::class.java, result)
        assertEquals(14L, success.account.userId)
        assertFalse(
            RegistrationResponse::class.java.declaredFields.any {
                it.name == "profileId" || it.name == "name"
            },
        )
        assertEquals(
            RegistrationRequest("person@example.com", "Password1!"),
            api.lastRequest,
        )
    }

    @Test
    @DisplayName("UC18 A3: 400 response maps to invalid registration")
    fun badRequestMapsToInvalidRegistration() = kotlinx.coroutines.test.runTest {
        val result = repositoryForStatus(400).register("person@example.com", "Password1!", null)

        val failure = assertInstanceOf(RegistrationResult.Failure::class.java, result)
        assertEquals(RegistrationFailureType.INVALID_REQUEST, failure.type)
        assertEquals("internal detail", failure.message)
    }

    @Test
    @DisplayName("UC18 A4: 409 response maps to the frozen duplicate-email message")
    fun conflictMapsToDuplicateEmail() = kotlinx.coroutines.test.runTest {
        val result = repositoryForStatus(409).register("person@example.com", "Password1!", null)

        val failure = assertInstanceOf(RegistrationResult.Failure::class.java, result)
        assertEquals(RegistrationFailureType.DUPLICATE_EMAIL, failure.type)
        assertEquals("An account with this email already exists.", failure.message)
    }

    @Test
    @DisplayName("UC18 A5: 500 response maps to a safe generic failure")
    fun serverErrorMapsToSafeFailure() = kotlinx.coroutines.test.runTest {
        val result = repositoryForStatus(500).register("person@example.com", "Password1!", null)

        val failure = assertInstanceOf(RegistrationResult.Failure::class.java, result)
        assertEquals(RegistrationFailureType.SERVER, failure.type)
        assertEquals("Registration could not be completed.", failure.message)
    }

    @Test
    @DisplayName("UC18 A6: network exception maps to a safe generic failure")
    fun networkExceptionMapsToSafeFailure() = kotlinx.coroutines.test.runTest {
        val api = FakeRegistrationApiService(exception = IOException("password=do-not-expose"))

        val result = ServerRegistrationRepository(api).register(
            "person@example.com",
            "Password1!",
            null,
        )

        val failure = assertInstanceOf(RegistrationResult.Failure::class.java, result)
        assertEquals("Registration could not be completed.", failure.message)
        assertFalse(failure.message.contains("do-not-expose"))
    }

    @Test
    fun invitationTokenIsTrimmedOnRegister() = kotlinx.coroutines.test.runTest {
        val api = FakeRegistrationApiService(
            response = Response.success(201, RegistrationResponse(14L, "person@example.com", true)),
        )

        ServerRegistrationRepository(api).register("person@example.com", "Password1!", "  tok  ")

        assertEquals("tok", api.lastRequest?.invitationToken)
    }

    @Test
    fun blankErrorBodyUsesInvalidRequestFallback() = kotlinx.coroutines.test.runTest {
        val result = ServerRegistrationRepository(
            FakeRegistrationApiService(
                response = Response.error(400, " ".toResponseBody("application/json".toMediaType())),
            ),
        ).register("person@example.com", "Password1!", null)

        val failure = assertInstanceOf(RegistrationResult.Failure::class.java, result)
        assertEquals("Invalid registration request.", failure.message)
    }

    @Test
    fun previewInvitationReturnsBodyOrNull() = kotlinx.coroutines.test.runTest {
        val preview = InvitationPreviewResponse("jamie@example.com", "Wong Family", false)
        val successApi = FakeRegistrationApiService(previewResponse = Response.success(preview))
        assertEquals(
            preview,
            ServerRegistrationRepository(successApi).previewInvitation("  tok  "),
        )
        assertEquals("tok", successApi.lastPreviewToken)

        assertEquals(
            null,
            ServerRegistrationRepository(
                FakeRegistrationApiService(
                    previewResponse = Response.error(404, "{}".toResponseBody("application/json".toMediaType())),
                ),
            ).previewInvitation("tok"),
        )
        assertEquals(
            null,
            ServerRegistrationRepository(
                FakeRegistrationApiService(previewException = IOException("offline")),
            ).previewInvitation("tok"),
        )
    }

    private fun repositoryForStatus(status: Int): ServerRegistrationRepository {
        val errorBody = "{\"message\":\"internal detail\"}"
            .toResponseBody("application/json".toMediaType())
        return ServerRegistrationRepository(
            FakeRegistrationApiService(response = Response.error(status, errorBody))
        )
    }

    private class FakeRegistrationApiService(
        private val response: Response<RegistrationResponse>? = null,
        private val exception: Exception? = null,
        private val previewResponse: Response<InvitationPreviewResponse> = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        ),
        private val previewException: Exception? = null,
    ) : RegistrationApiService {
        var lastRequest: RegistrationRequest? = null
        var lastPreviewToken: String? = null

        override suspend fun register(
            request: RegistrationRequest,
        ): Response<RegistrationResponse> {
            lastRequest = request
            exception?.let { throw it }
            return requireNotNull(response)
        }

        override suspend fun previewInvitation(
            token: String,
        ): Response<InvitationPreviewResponse> {
            lastPreviewToken = token
            previewException?.let { throw it }
            return previewResponse
        }
    }
}
