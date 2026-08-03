package sg.edu.nus.iss.canmakan.features.family.data

import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile

object FamilyProfileMapper {
    fun fromResponse(response: FamilyProfileResponse): DietaryProfile {
        return DietaryProfile(
            id = response.id,
            familyId = response.familyId,
            profileName = response.profileName.orEmpty(),
            relationship = response.relationship.orEmpty(),
            initials = response.initials.orEmpty(),
            isPrimary = response.isPrimary
        )
    }

    fun fromResponses(responses: List<FamilyProfileResponse>): List<DietaryProfile> {
        return responses.map(::fromResponse)
    }
}
