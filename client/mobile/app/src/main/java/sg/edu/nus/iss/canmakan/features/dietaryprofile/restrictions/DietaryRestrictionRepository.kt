package sg.edu.nus.iss.canmakan.features.userprofile

interface DietaryRestrictionRepository {
    suspend fun getAllDietaryRestrictions(): List<DietaryRestriction>

    suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String>
}