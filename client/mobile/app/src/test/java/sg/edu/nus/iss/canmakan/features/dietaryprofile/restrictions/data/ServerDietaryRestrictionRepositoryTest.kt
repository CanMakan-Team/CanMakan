package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Response
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction

/*
    Repository-layer test for UC1 (Update App User Dietary Profile), covering the
    "Edit Dietary Restrictions" feature reached via the profile drawer button and
    the toggle options in DietaryRestrictionSheet.

    @author Amelia
 */
@DisplayName("UC1: Edit Dietary Restrictions Android repository")
class ServerDietaryRestrictionRepositoryTest {

    @Test
    @DisplayName("UC1 R1: getAllDietaryRestrictions returns the catalog from the API service unchanged")
    fun getAllDietaryRestrictionsReturnsCatalog() = runTest {
        val catalog = listOf(
            DietaryRestriction(1L, "HALAL", "Halal", "RELIGIOUS"),
            DietaryRestriction(3L, "PEANUT_ALLERGY", "Peanut allergy", "ALLERGEN"),
            DietaryRestriction(5L, "VEGAN", "Vegan", "DIET"),
        )
        val api = FakeDietaryRestrictionApiService(allRestrictions = catalog)

        val result = ServerDietaryRestrictionRepository(api).getAllDietaryRestrictions()

        assertEquals(catalog, result)
    }

    @Test
    @DisplayName("UC1 R2: getAllDietaryRestrictions propagates a network failure rather than swallowing it")
    fun getAllDietaryRestrictionsPropagatesFailure() = runTest {
        val api = FakeDietaryRestrictionApiService(
            allRestrictionsException = IOException("network down"),
        )

        val outcome = runCatching {
            ServerDietaryRestrictionRepository(api).getAllDietaryRestrictions()
        }

        assertTrue(outcome.isFailure)
        assertInstanceOf(IOException::class.java, outcome.exceptionOrNull())
    }

    @Test
    @DisplayName("UC1 R3: getDietaryRestrictionsForProfile forwards the profile id and returns saved selections")
    fun getDietaryRestrictionsForProfileReturnsSelections() = runTest {
        val selections = mapOf(1L to "STRICT_AVOID", 3L to "INTOLERANCE")
        val api = FakeDietaryRestrictionApiService(profileRestrictions = selections)

        val result = ServerDietaryRestrictionRepository(api).getDietaryRestrictionsForProfile(42L)

        assertEquals(selections, result)
        assertEquals(42L, api.lastRequestedProfileId)
    }

    @Test
    @DisplayName("UC1 R4: getDietaryRestrictionsForProfile propagates a network failure rather than swallowing it")
    fun getDietaryRestrictionsForProfilePropagatesFailure() = runTest {
        val api = FakeDietaryRestrictionApiService(
            profileRestrictionsException = IOException("network down"),
        )

        val outcome = runCatching {
            ServerDietaryRestrictionRepository(api).getDietaryRestrictionsForProfile(42L)
        }

        assertTrue(outcome.isFailure)
        assertInstanceOf(IOException::class.java, outcome.exceptionOrNull())
    }

    @Test
    @DisplayName("UC1 R5: saveDietaryRestrictionSelections forwards profile id and selections and reports 2xx as success")
    fun saveDietaryRestrictionSelectionsSuccess() = runTest {
        val api = FakeDietaryRestrictionApiService(saveResponse = Response.success(Unit))
        val selections = mapOf(1L to "STRICT_AVOID", 5L to "PREFERENCE")

        val result = ServerDietaryRestrictionRepository(api)
            .saveDietaryRestrictionSelections(42L, selections)

        assertTrue(result)
        assertEquals(42L, api.lastSavedProfileId)
        assertEquals(selections, api.lastSavedSelections)
    }

    @Test
    @DisplayName("UC1 R6: saveDietaryRestrictionSelections reports a non-2xx response as failure, not an exception")
    fun saveDietaryRestrictionSelectionsMapsErrorResponseToFalse() = runTest {
        val errorBody = "{\"message\":\"profile not found\"}"
            .toResponseBody("application/json".toMediaType())
        val api = FakeDietaryRestrictionApiService(saveResponse = Response.error(404, errorBody))

        val result = ServerDietaryRestrictionRepository(api)
            .saveDietaryRestrictionSelections(42L, mapOf(1L to "STRICT_AVOID"))

        assertFalse(result)
    }

    @Test
    @DisplayName("UC1 R7: saveDietaryRestrictionSelections propagates a network failure rather than swallowing it")
    fun saveDietaryRestrictionSelectionsPropagatesFailure() = runTest {
        val api = FakeDietaryRestrictionApiService(saveException = IOException("network down"))

        val outcome = runCatching {
            ServerDietaryRestrictionRepository(api)
                .saveDietaryRestrictionSelections(42L, mapOf(1L to "STRICT_AVOID"))
        }

        assertTrue(outcome.isFailure)
        assertInstanceOf(IOException::class.java, outcome.exceptionOrNull())
    }

    @Test
    @DisplayName("UC1 R8: an empty selection map is forwarded as-is, e.g. clearing every restriction")
    fun saveDietaryRestrictionSelectionsForwardsEmptyMap() = runTest {
        val api = FakeDietaryRestrictionApiService(saveResponse = Response.success(Unit))

        val result = ServerDietaryRestrictionRepository(api)
            .saveDietaryRestrictionSelections(42L, emptyMap())

        assertTrue(result)
        assertEquals(emptyMap<Long, String>(), api.lastSavedSelections)
    }

    @Test
    fun nonpositiveProfileIdIsRejectedBeforeProfileApiCalls() = runTest {
        val api = FakeDietaryRestrictionApiService(saveResponse = Response.success(Unit))
        val repository = ServerDietaryRestrictionRepository(api)

        val loadOutcome = runCatching { repository.getDietaryRestrictionsForProfile(0L) }
        val saveOutcome = runCatching {
            repository.saveDietaryRestrictionSelections(0L, emptyMap())
        }

        assertTrue(loadOutcome.isFailure)
        assertTrue(saveOutcome.isFailure)
        assertEquals(null, api.lastRequestedProfileId)
        assertEquals(null, api.lastSavedProfileId)
    }

    private class FakeDietaryRestrictionApiService(
        private val allRestrictions: List<DietaryRestriction> = emptyList(),
        private val allRestrictionsException: Exception? = null,
        private val profileRestrictions: Map<Long, String> = emptyMap(),
        private val profileRestrictionsException: Exception? = null,
        private val saveResponse: Response<Unit>? = null,
        private val saveException: Exception? = null,
    ) : DietaryRestrictionApiService {
        var lastRequestedProfileId: Long? = null
        var lastSavedProfileId: Long? = null
        var lastSavedSelections: Map<Long, String>? = null

        override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> {
            allRestrictionsException?.let { throw it }
            return allRestrictions
        }

        override suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String> {
            lastRequestedProfileId = profileId
            profileRestrictionsException?.let { throw it }
            return profileRestrictions
        }

        override suspend fun saveDietaryRestrictionSelections(
            profileId: Long,
            selections: Map<Long, String>,
        ): Response<Unit> {
            lastSavedProfileId = profileId
            lastSavedSelections = selections
            saveException?.let { throw it }
            return requireNotNull(saveResponse)
        }
    }
}
