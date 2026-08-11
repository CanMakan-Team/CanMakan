package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data

// Network-backed implementation that forwards dietary restriction calls to the backend API service.
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import javax.inject.Inject

class ServerDietaryRestrictionRepository @Inject constructor(
    private val dietaryRestrictionApiService: DietaryRestrictionApiService
) : DietaryRestrictionRepository {

    override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> {
        return dietaryRestrictionApiService.getAllDietaryRestrictions()
    }

    override suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String> {
        require(profileId > 0) { "Dietary restrictions require a positive profile id." }
        return dietaryRestrictionApiService.getDietaryRestrictionsForProfile(profileId)
    }

    override suspend fun saveDietaryRestrictionSelections(
        profileId: Long,
        selections: Map<Long, String>
    ): Boolean {
        require(profileId > 0) { "Dietary restrictions require a positive profile id." }
        val response = dietaryRestrictionApiService.saveDietaryRestrictionSelections(profileId, selections)
        return response.isSuccessful
    }
}
