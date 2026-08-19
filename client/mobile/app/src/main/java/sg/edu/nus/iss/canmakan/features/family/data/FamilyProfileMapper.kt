package sg.edu.nus.iss.canmakan.features.family.data

import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile

object FamilyProfileMapper {
    fun fromActiveResponse(active: ActiveProfileResponse): DietaryProfile {
        val trimmedName = active.profileName.trim()
        val words = trimmedName.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val initials = if (words.size >= 2) {
            (words.first().take(1) + words.last().take(1)).uppercase()
        } else {
            trimmedName.take(minOf(2, trimmedName.length)).uppercase()
        }
        return DietaryProfile(
            id = active.profileId,
            familyId = active.familyId ?: 0L,
            profileName = active.profileName,
            relationship = active.relationship.orEmpty(),
            initials = initials.ifEmpty { "?" },
            isPrimary = active.isPrimary ?: false,
        )
    }

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
