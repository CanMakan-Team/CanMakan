package sg.edu.nus.iss.canmakan.shared.di

import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.shared.network.AssessmentResponse
import sg.edu.nus.iss.canmakan.shared.util.BACKEND_LOCAL_DATE_TIME_FORMATTER
import java.time.LocalDateTime

@DisplayName("Network Gson parses production-shaped scan payloads")
class NetworkGsonTest {

    @Test
    fun assessmentResponseParsesWithoutLenientMode() {
        val gson = GsonBuilder()
            .registerTypeAdapter(
                LocalDateTime::class.java,
                com.google.gson.JsonDeserializer { json, _, _ ->
                    LocalDateTime.parse(json.asString, BACKEND_LOCAL_DATE_TIME_FORMATTER)
                },
            )
            .create()

        val parsed = gson.fromJson(
            """
            {
              "verdict": "SAFE",
              "explanation": "No conflicts",
              "findings": [],
              "tier": "GREEN",
              "scanId": 19,
              "productName": "Rice",
              "barcode": "123"
            }
            """.trimIndent(),
            AssessmentResponse::class.java,
        )

        assertEquals("SAFE", parsed.verdict)
        assertEquals(19L, parsed.scanId)
        assertEquals("Rice", parsed.productName)
    }
}
