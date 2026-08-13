package sg.edu.nus.iss.canmakan.features.auth.data

import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class ServerRegistrationRepository @Inject constructor(
    private val registrationApiService: RegistrationApiService,
) : RegistrationRepository {

    override suspend fun register(
        email: String,
        password: String,
    ): RegistrationResult {
        return try {
            val response = registrationApiService.register(
                RegistrationRequest(
                    email = email,
                    password = password,
                )
            )
            val account = response.body()

            when {
                response.isSuccessful && account != null -> RegistrationResult.Success(account)
                response.code() == 400 -> RegistrationResult.Failure(
                    RegistrationFailureType.INVALID_REQUEST,
                    "Invalid registration request."
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
}
