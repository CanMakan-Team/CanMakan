package sg.edu.nus.iss.canmakan.features.family.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface FamilyProfileApiService {
    @GET("families/me")
    suspend fun getMyFamily(
        @Header("X-User-Id") userId: Long,
    ): Response<FamilyMeResponse>

    @POST("families")
    suspend fun createFamily(
        @Header("X-User-Id") userId: Long,
        @Body request: CreateFamilyRequestBody,
    ): Response<FamilyMeResponse>

    @GET("families/{familyId}/profiles")
    suspend fun getProfilesByFamilyId(
        @Path("familyId") familyId: Long
    ): List<FamilyProfileResponse>

    /**
     * (UC6) View Family Allergy Summary Grid
     */
    @GET("families/me/restriction-summary")
    suspend fun getFamilyRestrictionSummary(
        @Header("X-User-Id") userId: Long,
    ): Response<FamilyRestrictionSumRes>
}
