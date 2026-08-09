package sg.edu.nus.iss.canmakan.features.family.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import javax.inject.Inject

class FamilyProfileRepository @Inject constructor(
    private val apiService: FamilyProfileApiService
) {
    /**
     * Returns the caller's family context, or null when the user has no membership (HTTP 404).
     */
    suspend fun getMyFamily(userId: Long): FamilyMeResponse? {
        val response = apiService.getMyFamily(userId)
        if (response.code() == 404) {
            return null
        }
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        return response.body()
            ?: throw IllegalStateException("Empty body for GET /families/me")
    }

    /**
     * Creates a family circle. HTTP 409 (already a member) reloads `/me` instead of failing.
     */
    suspend fun createFamily(userId: Long, familyName: String): FamilyMeResponse {
        val response = apiService.createFamily(
            userId = userId,
            request = CreateFamilyRequestBody(familyName = familyName),
        )
        if (response.code() == 409) {
            return getMyFamily(userId)
                ?: throw IllegalStateException("Family already exists but GET /families/me returned 404")
        }
        if (!response.isSuccessful) {
            throw CreateFamilyException(messageFromError(response), response.code())
        }
        return response.body()
            ?: throw IllegalStateException("Empty body for POST /families")
    }

    suspend fun getProfilesForFamily(familyId: Long): List<DietaryProfile> {
        return FamilyProfileMapper.fromResponses(apiService.getProfilesByFamilyId(familyId))
    }

    private fun messageFromError(response: Response<*>): String {
        val raw = response.errorBody()?.string().orEmpty()
        if (raw.isBlank()) {
            return "Could not create family circle."
        }
        // Lightweight extract so JVM unit tests do not need Android org.json.
        val match = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(raw)
        val extracted = match?.groupValues?.getOrNull(1).orEmpty()
        return extracted.ifBlank { "Could not create family circle." }
    }

    /**
     * (UC6) View Family Allergy Summary Grid
     */
    suspend fun getFamilyRestrictionSummary(userId: Long): Result<FamilyRestrictionSumRes> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getFamilyRestrictionSummary(userId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to Fetch Family Restriction Summary, HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

class CreateFamilyException(
    message: String,
    val statusCode: Int,
) : Exception(message)
