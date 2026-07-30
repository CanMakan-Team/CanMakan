package sg.edu.nus.iss.canmakan.dietaryrestriction

interface DietaryRestrictionRepository {

    suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> {}
}