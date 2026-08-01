package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data

// Abstraction for dietary restriction data access so the UI does not depend on a specific source.
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction

interface DietaryRestrictionRepository {
    suspend fun getAllDietaryRestrictions(): List<DietaryRestriction>

    suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String>

    suspend fun saveDietaryRestrictionSelections(profileId: Long, selections: Map<Long, String>): Boolean
}
