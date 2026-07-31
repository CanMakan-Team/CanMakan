package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.repo

import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.DietaryRestriction

interface DietaryRestrictionRepository {
    suspend fun getAllDietaryRestrictions(): List<DietaryRestriction>

    suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String>

    suspend fun saveDietaryRestrictionSelections(profileId: Long, selections: Map<Long, String>)
}