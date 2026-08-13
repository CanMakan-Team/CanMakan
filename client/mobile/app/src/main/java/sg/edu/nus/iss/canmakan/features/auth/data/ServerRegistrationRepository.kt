package sg.edu.nus.iss.canmakan.features.auth.data

import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import retrofit2.Response

class ServerRegistrationRepository @Inject constructor(
    private val registrationApiService: RegistrationApiService,
) : RegistrationRepository {

    override suspend fun register(
        email: String,
        password: String,
        invitationToken: String?,
    ): RegistrationResult {
        return try {
            val response = registrationApiService.register(
                RegistrationRequest(
                    email = email,
                    password = password,
                    invitationToken = invitationToken?.trim()?.takeIf { it.isNotEmpty() },
                )
            )
            val account = response.body()

            when {
                response.isSuccessful && account != null -> RegistrationResult.Success(account)
                response.code() == 400 -> RegistrationResult.Failure(
                    RegistrationFailureType.INVALID_REQUEST,
                    messageFromError(response) ?: "Invalid registration request.",
                )
                response.code() == 409 -> RegistrationResult.Failure(
                    RegistrationFailureType.DUPLICATE_EMAIL,
                    "An account with this email already exists."
                )
                else -> RegistrationResult.Failure(
                    RegistrationFailureType.SERVER,
                    "Registration could not be completed."
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            RegistrationResult.Failure(
                RegistrationFailureType.SERVER,
                "Registration could not be completed."
            )
        }
    }

    override suspend fun previewInvitation(invitationToken: String): InvitationPreviewResponse? {
        return try {
            val response = registrationApiService.previewInvitation(invitationToken.trim())
            if (response.isSuccessful) response.body() else null
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }

    private fun messageFromError(response: Response<*>): String? {
        val raw = response.errorBody()?.string().orEmpty()
        if (raw.isBlank()) {
            return null
        }
        val match = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(raw)
        return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }
}
