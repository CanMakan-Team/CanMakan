package sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import retrofit2.Response

class ServerSelfProfileRepositoryTest {
    @Test
    fun requestUsesProfileNameRestrictionIdsAndSupportedSeveritiesWithoutUserId() {
        val request = CreateSelfProfileRequest(
            profileName = "Person Name",
            restrictions = mapOf(
                2L to ProfileRestrictionSeverity.STRICT_AVOID,
                5L to ProfileRestrictionSeverity.INTOLERANCE,
            ),
        )

        val json = Gson().toJson(request)

        assertEquals(
            "{\"profileName\":\"Person Name\",\"restrictions\":{\"2\":\"STRICT_AVOID\",\"5\":\"INTOLERANCE\"}}",
            json,
        )
        assertFalse(json.contains("userId"))
        assertEquals(
            setOf("STRICT_AVOID", "INTOLERANCE"),
            ProfileRestrictionSeverity.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun createdResponseIsValidatedAndReturned() = kotlinx.coroutines.test.runTest {
        val api = FakeApi(
            Response.success(
                201,
                SelfProfileResponse(
                    profileId = 77L,
                    profileName = "Person Name",
                    relationship = "SELF",
                    active = true,
                    restrictions = mapOf(2L to "STRICT_AVOID"),
                ),
            ),
        )

        val result = ServerSelfProfileRepository(api).createSelfProfile(
            " Person Name ",
            mapOf(2L to ProfileRestrictionSeverity.STRICT_AVOID),
        )

        val created = assertInstanceOf(SelfProfileSetupResult.Created::class.java, result)
        assertEquals(77L, created.profile.profileId)
        assertEquals("Person Name", api.lastRequest?.profileName)
    }

    @Test
    fun successfulStatusOtherThan201IsRejected() = kotlinx.coroutines.test.runTest {
        listOf(200, 202).forEach { status ->
            val result = ServerSelfProfileRepository(
                FakeApi(
                    Response.success(
                        status,
                        SelfProfileResponse(77L, "Person", "SELF", true, emptyMap()),
                    ),
                ),
            ).createSelfProfile(
                "Person",
                mapOf(2L to ProfileRestrictionSeverity.STRICT_AVOID),
            )

            assertInstanceOf(SelfProfileSetupResult.Failure::class.java, result)
        }
    }

    @Test
    fun nonpositiveProfileIdIsRejected() = kotlinx.coroutines.test.runTest {
        listOf(0L, -1L).forEach { profileId ->
            val result = ServerSelfProfileRepository(
                FakeApi(
                    Response.success(
                        201,
                        SelfProfileResponse(profileId, "Person", "SELF", true, emptyMap()),
                    ),
                ),
            ).createSelfProfile(
                "Person",
                mapOf(2L to ProfileRestrictionSeverity.STRICT_AVOID),
            )

            assertInstanceOf(SelfProfileSetupResult.Failure::class.java, result)
        }
    }

    @Test
    fun conflictIsMappedForDeliberateResolution() = kotlinx.coroutines.test.runTest {
        val result = repositoryForStatus(409).createSelfProfile(
            "Person Name",
            mapOf(2L to ProfileRestrictionSeverity.STRICT_AVOID),
        )

        assertEquals(SelfProfileSetupResult.AlreadyExists, result)
    }

    @Test
    fun authenticationAndAuthorizationStatusesRemainDistinct() = kotlinx.coroutines.test.runTest {
        val unauthorized = repositoryForStatus(401).createSelfProfile(
            "Person Name",
            mapOf(2L to ProfileRestrictionSeverity.STRICT_AVOID),
        )
        val forbidden = repositoryForStatus(403).createSelfProfile(
            "Person Name",
            mapOf(2L to ProfileRestrictionSeverity.STRICT_AVOID),
        )

        assertEquals(SelfProfileSetupResult.Unauthenticated, unauthorized)
        assertEquals(SelfProfileSetupResult.Forbidden, forbidden)
    }

    private fun repositoryForStatus(status: Int): ServerSelfProfileRepository {
        val error = "{}".toResponseBody("application/json".toMediaType())
        return ServerSelfProfileRepository(FakeApi(Response.error(status, error)))
    }

    private class FakeApi(
        private val response: Response<SelfProfileResponse>,
    ) : SelfProfileApiService {
        var lastRequest: CreateSelfProfileRequest? = null

        override suspend fun createSelfProfile(
            request: CreateSelfProfileRequest,
        ): Response<SelfProfileResponse> {
            lastRequest = request
            return response
        }
    }
}
