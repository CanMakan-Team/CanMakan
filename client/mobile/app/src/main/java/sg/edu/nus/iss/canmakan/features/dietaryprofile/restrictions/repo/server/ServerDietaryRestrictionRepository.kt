package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.repo.server

import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.repo.DietaryRestrictionRepository
import javax.inject.Inject

class ServerDietaryRestrictionRepository @Inject constructor(
    private val dietaryRestrictionApiService: DietaryRestrictionApiService
): DietaryRestrictionRepository{

    override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> {
        return dietaryRestrictionApiService.getAllDietaryRestrictions()
    }

    override suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String> {
        return dietaryRestrictionApiService.getDietaryRestrictionsForProfile(profileId)
    }

    override suspend fun saveDietaryRestrictionSelections(
        profileId: Long,
        selections: Map<Long, String>
    ) {
        return dietaryRestrictionApiService.saveDietaryRestrictionSelections(profileId, selections)
    }

}