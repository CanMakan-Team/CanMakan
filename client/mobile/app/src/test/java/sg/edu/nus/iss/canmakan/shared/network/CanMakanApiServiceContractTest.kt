package sg.edu.nus.iss.canmakan.shared.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.http.GET
import retrofit2.http.POST

@DisplayName("Scan Retrofit paths are relative to the /api/ base URL")
class CanMakanApiServiceContractTest {

    @Test
    fun scanEndpointsDoNotUseRootAbsoluteApiPaths() {
        assertEquals("scan/validate", method("validateBarcode").getAnnotation(POST::class.java).value)
        assertEquals("scan/assess", method("assessBarcode").getAnnotation(POST::class.java).value)
        assertEquals("scan/{scanId}/feedback", method("submitScanFeedback").getAnnotation(POST::class.java).value)
        assertEquals(
            "profiles/{profileId}/recommendations",
            method("getRecommendations").getAnnotation(GET::class.java).value,
        )
        listOf("validateBarcode", "assessBarcode", "submitScanFeedback", "getRecommendations").forEach { name ->
            val path = method(name).annotations
                .mapNotNull { annotation ->
                    when (annotation) {
                        is POST -> annotation.value
                        is GET -> annotation.value
                        else -> null
                    }
                }
                .single()
            assertFalse(path.startsWith("/"), name)
        }
    }

    private fun method(name: String) = CanMakanApiService::class.java.declaredMethods.single {
        it.name == name
    }
}
