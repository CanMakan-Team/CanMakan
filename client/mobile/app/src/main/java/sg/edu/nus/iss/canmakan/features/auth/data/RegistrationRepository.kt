package sg.edu.nus.iss.canmakan.features.auth.data

enum class RegistrationFailureType {
    INVALID_REQUEST,
    DUPLICATE_EMAIL,
    SERVER,
}

sealed interface RegistrationResult {
    data class Success(val account: RegistrationResponse) : RegistrationResult

    data class Failure(
        val type: RegistrationFailureType,
        val message: String,
    ) : RegistrationResult
}

interface RegistrationRepository {
    suspend fun register(name: String, email: String, password: String): RegistrationResult
}
