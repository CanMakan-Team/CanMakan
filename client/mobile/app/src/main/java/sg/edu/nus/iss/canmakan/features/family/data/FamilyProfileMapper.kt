package sg.edu.nus.iss.canmakan.features.family.data

import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile

object FamilyProfileMapper {
    fun fromResponse(response: FamilyProfileResponse): DietaryProfile {
        return DietaryProfile(
            id = response.id,
            name = response.name.orEmpty(),
            role = response.role.orEmpty(),
            initials = response.initials.orEmpty()
        )
    }

    fun fromResponses(responses: List<FamilyProfileResponse>): List<DietaryProfile> {
        return responses.map(::fromResponse)
    }
}
