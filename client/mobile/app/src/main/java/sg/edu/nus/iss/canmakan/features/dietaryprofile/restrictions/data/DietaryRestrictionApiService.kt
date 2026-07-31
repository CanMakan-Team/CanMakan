package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction

interface DietaryRestrictionApiService {
    @GET("restrictions")
    suspend fun getAllDietaryRestrictions(): List<DietaryRestriction>

    @GET("profiles/{profileId}/restrictions")
    suspend fun getDietaryRestrictionsForProfile(
        @Path("profileId") profileId: Long
    ): Map<Long, String>

    @PUT("profiles/{profileId}/restrictions")
    suspend fun saveDietaryRestrictionSelections(
        @Path("profileId") profileId: Long,
        @Body selections: Map<Long, String>
    )
}
