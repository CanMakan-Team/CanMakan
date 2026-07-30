package sg.edu.nus.iss.canmakan.features.dietaryprofile

interface DietaryRestrictionRepository {
    suspend fun getAllDietaryRestrictions(): List<DietaryRestriction>
}