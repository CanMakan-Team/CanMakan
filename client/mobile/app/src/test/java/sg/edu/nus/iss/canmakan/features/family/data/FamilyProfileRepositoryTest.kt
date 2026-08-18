package sg.edu.nus.iss.canmakan.features.family.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import retrofit2.Response

@DisplayName("UC8 / UC11: family membership repository")
class FamilyProfileRepositoryTest {

    @Test
    @DisplayName("GET /families/me 200 returns family context")
    fun getMyFamilyReturnsBodyOnSuccess() = runBlocking {
        val expected = FamilyMeResponse(
            familyId = 50L,
            familyName = "Wong Family",
            memberRole = "PRIMARY_ADMIN",
            selfProfileId = 77L,
            createdByUserId = 14L,
        )
        val repository = FamilyProfileRepository(FakeFamilyProfileApiService(meResponse = Response.success(expected)))

        val result = repository.getMyFamily()

        assertEquals(expected, result)
    }

    @Test
    @DisplayName("GET /families/me 404 means no family membership")
    fun getMyFamilyReturnsNullOnNotFound() = runBlocking {
        val body = """{"message":"not a member"}""".toResponseBody("application/json".toMediaType())
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(meResponse = Response.error(404, body)),
        )

        val result = repository.getMyFamily()

        assertNull(result)
    }

    @Test
    @DisplayName("POST /families 201 returns created family context")
    fun createFamilyReturnsBodyOnSuccess() = runBlocking {
        val expected = FamilyMeResponse(
            familyId = 50L,
            familyName = "Wong Family",
            memberRole = "PRIMARY_ADMIN",
            selfProfileId = 77L,
            createdByUserId = 14L,
        )
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(createResponse = Response.success(201, expected)),
        )

        val result = repository.createFamily("Wong Family")

        assertEquals(expected, result)
    }

    @Test
    @DisplayName("POST /families 409 reloads GET /families/me")
    fun createFamilyReloadsMeOnConflict() = runBlocking {
        val existing = FamilyMeResponse(
            familyId = 50L,
            familyName = "Wong Family",
            memberRole = "PRIMARY_ADMIN",
            selfProfileId = 77L,
            createdByUserId = 14L,
        )
        val conflictBody = """{"message":"already a member"}""".toResponseBody("application/json".toMediaType())
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(
                meResponse = Response.success(existing),
                createResponse = Response.error(409, conflictBody),
            ),
        )

        val result = repository.createFamily("Wong Family")

        assertEquals(existing, result)
    }

    @Test
    @DisplayName("POST /families 400 throws FamilyApiException with API message")
    fun createFamilyThrowsOnBadRequest() {
        val body = """{"message":"Family name is required."}""".toResponseBody("application/json".toMediaType())
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(createResponse = Response.error(400, body)),
        )

        val exception = assertThrows(FamilyApiException::class.java) {
            runBlocking { repository.createFamily("  ") }
        }

        assertEquals("Family name is required.", exception.message)
        assertEquals(400, exception.statusCode)
    }

    @Test
    @DisplayName("GET /users/me/preferences/notifications 200 returns enabled flag")
    fun getNotificationPreferenceReturnsBodyOnSuccess() = runBlocking {
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(
                notificationPreferenceResponse = Response.success(
                    NotificationPreferenceResponse(notificationsEnabled = true),
                ),
            ),
        )

        assertEquals(true, repository.getNotificationPreference())
    }

    @Test
    @DisplayName("GET /users/me/preferences/notifications non-2xx throws FamilyApiException")
    fun getNotificationPreferenceThrowsOnError() {
        val body = """{"message":"Preference unavailable."}""".toResponseBody("application/json".toMediaType())
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(
                notificationPreferenceResponse = Response.error(503, body),
            ),
        )

        val exception = assertThrows(FamilyApiException::class.java) {
            runBlocking { repository.getNotificationPreference() }
        }

        assertEquals("Preference unavailable.", exception.message)
        assertEquals(503, exception.statusCode)
    }

    @Test
    @DisplayName("GET /users/me/preferences/notifications 200 with empty body throws")
    fun getNotificationPreferenceThrowsOnEmptyBody() {
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(
                notificationPreferenceResponse = Response.success(null),
            ),
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.getNotificationPreference() }
        }

        assertEquals("Empty body for GET /users/me/preferences/notifications", exception.message)
    }

    @Test
    @DisplayName("PUT /users/me/preferences/notifications 200 returns updated flag")
    fun setNotificationPreferenceReturnsBodyOnSuccess() = runBlocking {
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(
                setNotificationPreferenceResponse = Response.success(
                    NotificationPreferenceResponse(notificationsEnabled = false),
                ),
            ),
        )

        assertEquals(false, repository.setNotificationPreference(false))
    }

    @Test
    fun restrictionSummaryReturnsBodyOnSuccess() = runBlocking {
        val expected = FamilyRestrictionSumRes(familyMembers = emptyList())
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(summaryResponse = Response.success(expected)),
        )
        assertEquals(expected, repository.getFamilyRestrictionSummary())
    }

    @Test
    fun restrictionSummaryThrowsOnErrorAndEmptyBody() {
        val invalidJson = FamilyProfileRepository(
            FakeFamilyProfileApiService(
                summaryResponse = Response.error(500, "not-json".toResponseBody("application/json".toMediaType())),
            ),
        )
        val invalid = assertThrows(FamilyApiException::class.java) {
            runBlocking { invalidJson.getFamilyRestrictionSummary() }
        }
        assertEquals("Could not create family circle.", invalid.message)

        val blankBody = FamilyProfileRepository(
            FakeFamilyProfileApiService(
                summaryResponse = Response.error(500, " ".toResponseBody("application/json".toMediaType())),
            ),
        )
        assertEquals(
            "Could not create family circle.",
            assertThrows(FamilyApiException::class.java) {
                runBlocking { blankBody.getFamilyRestrictionSummary() }
            }.message,
        )

        val empty = FamilyProfileRepository(
            FakeFamilyProfileApiService(summaryResponse = Response.success(null)),
        )
        assertEquals(
            "Empty body for GET /families/me/restriction-summary",
            assertThrows(IllegalStateException::class.java) {
                runBlocking { empty.getFamilyRestrictionSummary() }
            }.message,
        )
    }

    @Test
    @DisplayName("PUT /users/me/preferences/notifications non-2xx throws FamilyApiException")
    fun setNotificationPreferenceThrowsOnError() {
        val body = """{"message":"Could not save preference."}""".toResponseBody("application/json".toMediaType())
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(
                setNotificationPreferenceResponse = Response.error(400, body),
            ),
        )

        val exception = assertThrows(FamilyApiException::class.java) {
            runBlocking { repository.setNotificationPreference(true) }
        }

        assertEquals("Could not save preference.", exception.message)
        assertEquals(400, exception.statusCode)
    }

    @Test
    @DisplayName("PUT /users/me/preferences/notifications 200 with empty body throws")
    fun setNotificationPreferenceThrowsOnEmptyBody() {
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(
                setNotificationPreferenceResponse = Response.success(null),
            ),
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.setNotificationPreference(true) }
        }

        assertEquals("Empty body for PUT /users/me/preferences/notifications", exception.message)
    }

    private class FakeFamilyProfileApiService(
        private val meResponse: Response<FamilyMeResponse> = Response.error(
            404,
            "{}".toResponseBody("application/json".toMediaType()),
        ),
        private val createResponse: Response<FamilyMeResponse> = Response.error(
            500,
            "{}".toResponseBody("application/json".toMediaType()),
        ),
        private val notificationPreferenceResponse: Response<NotificationPreferenceResponse> = Response.success(
            NotificationPreferenceResponse(notificationsEnabled = true),
        ),
        private val setNotificationPreferenceResponse: Response<NotificationPreferenceResponse> = Response.success(
            NotificationPreferenceResponse(notificationsEnabled = true),
        ),
        private val summaryResponse: Response<FamilyRestrictionSumRes> = Response.error(
            500,
            "{}".toResponseBody("application/json".toMediaType()),
        ),
    ) : FamilyProfileApiService {
        override suspend fun getMyFamily(): Response<FamilyMeResponse> = meResponse

        override suspend fun getFamilyMembers(): Response<List<FamilyMemberRosterItem>> =
            Response.success(emptyList())

        override suspend fun createFamily(
            request: CreateFamilyRequestBody,
        ): Response<FamilyMeResponse> = createResponse

        override suspend fun getProfilesByFamilyId(familyId: Long): List<FamilyProfileResponse> = emptyList()

        override suspend fun getActiveProfile(): Response<ActiveProfileResponse> =
            Response.error(404, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun setActiveProfile(
            request: SetActiveProfileRequestBody,
        ): Response<ActiveProfileResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun getNotificationPreference(): Response<NotificationPreferenceResponse> =
            notificationPreferenceResponse

        override suspend fun setNotificationPreference(
            request: SetNotificationPreferenceRequestBody,
        ): Response<NotificationPreferenceResponse> = setNotificationPreferenceResponse

        override suspend fun getFamilyRestrictionSummary(): Response<FamilyRestrictionSumRes> =
            summaryResponse

        override suspend fun searchUserByEmail(email: String): Response<UserSearchResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun createInvitation(
            request: CreateInvitationRequestBody,
        ): Response<InvitationResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun claimInvitation(
            request: ClaimInvitationRequestBody,
        ): Response<FamilyMeResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun listMyInvitations(): Response<List<PendingInvitationResponse>> =
            Response.success(emptyList())

        override suspend fun acceptInvitation(token: String): Response<FamilyMeResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun declineInvitation(token: String): Response<Unit> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))

        override suspend fun createDependantProfile(
            request: CreateDependantProfileRequestBody,
        ): Response<DependantProfileResponse> =
            Response.error(500, "{}".toResponseBody("application/json".toMediaType()))
    }
}
