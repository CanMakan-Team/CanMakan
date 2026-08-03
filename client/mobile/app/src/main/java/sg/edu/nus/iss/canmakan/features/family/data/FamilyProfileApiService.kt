package sg.edu.nus.iss.canmakan.features.family.data

import retrofit2.http.GET
import retrofit2.http.Path

interface FamilyProfileApiService {
    @GET("families/{familyId}/profiles")
    suspend fun getProfilesByFamilyId(
        @Path("familyId") familyId: Long
    ): List<FamilyProfileResponse>
}
