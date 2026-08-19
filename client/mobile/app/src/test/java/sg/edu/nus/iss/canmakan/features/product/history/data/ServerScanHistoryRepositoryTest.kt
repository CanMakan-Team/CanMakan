package sg.edu.nus.iss.canmakan.features.product.history.data

import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.product.model.FindingsJson
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ScanHistoryEntry
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict

/*
    Repository-layer test for View Scan History, reached via the History icon
    in the bottom nav bar.
 */
@DisplayName("View Scan History: Android repository")
class ServerScanHistoryRepositoryTest {

    @Test
    @DisplayName("R1: getScanHistoryForProfile forwards the profile id and returns the entries from the API service")
    fun getScanHistoryForProfileReturnsEntries() = runTest {
        val entries = listOf(
            ScanHistoryEntry(
                id = 5L,
                profileId = 1L,
                barcode = "675747001018",
                product = Product("Barley Malt Crackers", "Unbranded", "675747001018"),
                scannedAt = LocalDateTime.of(2026, 1, 5, 9, 0, 0),
                verdict = ScanVerdict.UNSAFE,
                findingsJson = FindingsJson(matchedRules = listOf("GLUTEN_ALLERGY")),
                aiExplanation = "Contains barley malt extract which contains gluten.",
            ),
            ScanHistoryEntry(
                id = 1L,
                profileId = 1L,
                barcode = "95500539",
                product = Product("Sardines in tomato sauce", "Ayam Brand", "95500539"),
                scannedAt = LocalDateTime.of(2025, 12, 27, 9, 0, 0),
                verdict = ScanVerdict.SAFE,
                findingsJson = FindingsJson(),
                aiExplanation = "This product contains no gluten ingredients or wheat derivatives.",
            ),
        )
        val api = FakeScanHistoryApiService(entries = entries)

        val result = ServerScanHistoryRepository(api).getScanHistoryForProfile(1L)

        assertEquals(entries, result)
        assertEquals(1L, api.lastRequestedProfileId)
    }

    @Test
    @DisplayName("R2: getScanHistoryForProfile returns an empty list for a profile with no scans")
    fun getScanHistoryForProfileReturnsEmptyListWhenNoScans() = runTest {
        val api = FakeScanHistoryApiService(entries = emptyList())

        val result = ServerScanHistoryRepository(api).getScanHistoryForProfile(9_999L)

        assertEquals(emptyList<ScanHistoryEntry>(), result)
    }

    @Test
    @DisplayName("R3: getScanHistoryForProfile propagates a network failure rather than swallowing it")
    fun getScanHistoryForProfilePropagatesFailure() = runTest {
        val api = FakeScanHistoryApiService(exception = IOException("network down"))

        val outcome = runCatching {
            ServerScanHistoryRepository(api).getScanHistoryForProfile(1L)
        }

        assertTrue(outcome.isFailure)
        assertInstanceOf(IOException::class.java, outcome.exceptionOrNull())
    }

    @Test
    fun nonpositiveProfileIdIsRejectedBeforeApiCall() = runTest {
        val api = FakeScanHistoryApiService()

        val outcome = runCatching {
            ServerScanHistoryRepository(api).getScanHistoryForProfile(0L)
        }

        assertTrue(outcome.isFailure)
        assertEquals(null, api.lastRequestedProfileId)
    }

    private class FakeScanHistoryApiService(
        private val entries: List<ScanHistoryEntry> = emptyList(),
        private val exception: Exception? = null,
    ) : ScanHistoryApiService {
        var lastRequestedProfileId: Long? = null

        override suspend fun getScanHistoryForProfile(profileId: Long): List<ScanHistoryEntry> {
            lastRequestedProfileId = profileId
            exception?.let { throw it }
            return entries
        }
    }
}
