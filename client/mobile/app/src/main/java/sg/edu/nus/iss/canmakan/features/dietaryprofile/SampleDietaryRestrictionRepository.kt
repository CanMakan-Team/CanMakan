package sg.edu.nus.iss.canmakan.features.dietaryprofile

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleDietaryRestrictionRepository @Inject constructor() : DietaryRestrictionRepository {
    override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> = listOf(
        DietaryRestriction(
            id = 1,
            code = "HALAL",
            displayName = "Halal",
            category = "RELIGIOUS",
            description = "Food prepared in accordance with halal dietary requirements",
        ),
        DietaryRestriction(
            id = 2,
            code = "KOSHER",
            displayName = "Kosher",
            category = "RELIGIOUS",
            description = "Food prepared in accordance with kosher dietary requirements",
        ),
        DietaryRestriction(
            id = 3,
            code = "PEANUT_ALLERGY",
            displayName = "Peanut allergy",
            category = "ALLERGEN",
            description = "Avoid peanuts and peanut-derived ingredients",
        ),
        DietaryRestriction(
            id = 4,
            code = "DAIRY_FREE",
            displayName = "Dairy free",
            category = "ALLERGEN",
            description = "Avoid dairy products and milk-derived ingredients",
        ),
        DietaryRestriction(
            id = 5,
            code = "VEGAN",
            displayName = "Vegan",
            category = "DIET",
            description = "No animal-derived ingredients",
        ),
        DietaryRestriction(
            id = 6,
            code = "VEGETARIAN",
            displayName = "Vegetarian",
            category = "DIET",
            description = "No meat or fish",
        ),
        DietaryRestriction(
            id = 7,
            code = "LOW_SUGAR",
            displayName = "Low sugar",
            category = "DIET",
            description = "Prefer lower sugar content",
        ),
    )
}
