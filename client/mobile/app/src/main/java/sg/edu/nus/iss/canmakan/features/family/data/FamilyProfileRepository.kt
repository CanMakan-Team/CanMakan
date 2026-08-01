package sg.edu.nus.iss.canmakan.features.family.data

import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import javax.inject.Inject

class FamilyProfileRepository @Inject constructor(
    private val apiService: FamilyProfileApiService
) {
    suspend fun getProfilesForFamily(familyId: Long): List<DietaryProfile> {
        return FamilyProfileMapper.fromResponses(apiService.getProfilesByFamilyId(familyId))
    }
}
