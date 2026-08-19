package sg.edu.nus.iss.canmakan.features.auth.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RegistrationRepositoryTypesTest {

    @Test
    fun failureCarriesTypeAndMessage() {
        val failure = RegistrationResult.Failure(
            RegistrationFailureType.DUPLICATE_EMAIL,
            "Email already registered.",
        )
        assertEquals(RegistrationFailureType.DUPLICATE_EMAIL, failure.type)
        assertEquals("Email already registered.", failure.message)
    }
}
