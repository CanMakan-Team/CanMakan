package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.data

// Local sample implementation that returns hard-coded dietary restriction data for development and testing.
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.ProfileRestriction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleDietaryRestrictionRepository @Inject constructor() : DietaryRestrictionRepository {
    override suspend fun getAllDietaryRestrictions(): List<DietaryRestriction> = listOf(
        DietaryRestriction(
            id = 1L,
            code = "HALAL",
            displayName = "Halal",
            category = "RELIGIOUS",
            description = "Food prepared in accordance with halal dietary requirements",
        ),
        DietaryRestriction(
            id = 2L,
            code = "KOSHER",
            displayName = "Kosher",
            category = "RELIGIOUS",
            description = "Food prepared in accordance with kosher dietary requirements",
        ),
        DietaryRestriction(
            id = 3L,
            code = "PEANUT_ALLERGY",
            displayName = "Peanut allergy",
            category = "ALLERGEN",
            description = "Avoid peanuts and peanut-derived ingredients",
        ),
        DietaryRestriction(
            id = 4L,
            code = "DAIRY",
            displayName = "Lactose Intolerance",
            category = "ALLERGEN",
            description = "Avoid dairy products, milk-derived ingredients, and lactose",
        ),
        DietaryRestriction(
            id = 5L,
            code = "VEGAN",
            displayName = "Vegan",
            category = "DIET",
            description = "No animal-derived ingredients",
        ),
        DietaryRestriction(
            id = 6L,
            code = "VEGETARIAN",
            displayName = "Vegetarian",
            category = "DIET",
            description = "No meat or fish",
        ),
        DietaryRestriction(
            id = 7L,
            code = "LOW_SUGAR",
            displayName = "Low sugar",
            category = "DIET",
            description = "Prefer lower sugar content",
        ),
    )

    private var sampleProfileRestriction: MutableList<ProfileRestriction> = mutableListOf(
        ProfileRestriction(
            dietaryProfileId = 1L,
            dietaryRestrictionId = 1L,
            severityLevel = "STRICT_AVOID"
        ),
        ProfileRestriction(
            dietaryProfileId = 1L,
            dietaryRestrictionId = 3L,
            severityLevel = "STRICT_AVOID"
        ),
        ProfileRestriction(
            dietaryProfileId = 2L,
            dietaryRestrictionId = 2L,
            severityLevel = "STRICT_AVOID"
        ),
        ProfileRestriction(
            dietaryProfileId = 2L,
            dietaryRestrictionId = 5L,
            severityLevel = "STRICT_AVOID"
        )
    )

    override suspend fun getDietaryRestrictionsForProfile(profileId: Long): Map<Long, String> {
        require(profileId > 0) { "Dietary restrictions require a positive profile id." }
        return sampleProfileRestriction
            .filter { it.dietaryProfileId == profileId }
            .associate { it.dietaryRestrictionId to it.severityLevel }
    }

    override suspend fun saveDietaryRestrictionSelections(
        profileId: Long,
        selections: Map<Long, String>
    ): Boolean {
        require(profileId > 0) { "Dietary restrictions require a positive profile id." }
        sampleProfileRestriction.removeAll { it.dietaryProfileId == profileId }

        val newEntries = selections.map { (restrictionId, severity) ->
            ProfileRestriction(
                dietaryProfileId = profileId,
                dietaryRestrictionId = restrictionId,
                severityLevel = severity
            )
        }
        sampleProfileRestriction.addAll(newEntries)
        return true
    }
}
