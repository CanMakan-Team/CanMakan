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

        val result = repository.getMyFamily(14L)

        assertEquals(expected, result)
    }

    @Test
    @DisplayName("GET /families/me 404 means no family membership")
    fun getMyFamilyReturnsNullOnNotFound() = runBlocking {
        val body = """{"message":"not a member"}""".toResponseBody("application/json".toMediaType())
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(meResponse = Response.error(404, body)),
        )

        val result = repository.getMyFamily(14L)

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

        val result = repository.createFamily(14L, "Wong Family")

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

        val result = repository.createFamily(14L, "Wong Family")

        assertEquals(existing, result)
    }

    @Test
    @DisplayName("POST /families 400 throws CreateFamilyException with API message")
    fun createFamilyThrowsOnBadRequest() {
        val body = """{"message":"Family name is required."}""".toResponseBody("application/json".toMediaType())
        val repository = FamilyProfileRepository(
            FakeFamilyProfileApiService(createResponse = Response.error(400, body)),
        )

        val exception = assertThrows(CreateFamilyException::class.java) {
            runBlocking { repository.createFamily(14L, "  ") }
        }

        assertEquals("Family name is required.", exception.message)
        assertEquals(400, exception.statusCode)
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
    ) : FamilyProfileApiService {
        override suspend fun getMyFamily(userId: Long): Response<FamilyMeResponse> = meResponse

        override suspend fun createFamily(
            userId: Long,
            request: CreateFamilyRequestBody,
        ): Response<FamilyMeResponse> = createResponse

        override suspend fun getProfilesByFamilyId(familyId: Long): List<FamilyProfileResponse> = emptyList()
    }
}
