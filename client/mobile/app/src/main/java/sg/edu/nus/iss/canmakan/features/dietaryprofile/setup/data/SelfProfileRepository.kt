package sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data

import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository

sealed interface SelfProfileSetupResult {
    data class Created(val profile: SelfProfileResponse) : SelfProfileSetupResult
    data class InvalidRequest(val message: String) : SelfProfileSetupResult
    data object Unauthenticated : SelfProfileSetupResult
    data object Forbidden : SelfProfileSetupResult
    data object AlreadyExists : SelfProfileSetupResult
    data class Failure(val message: String) : SelfProfileSetupResult
}

interface SelfProfileRepository {
    suspend fun createSelfProfile(
        profileName: String,
        restrictions: Map<Long, ProfileRestrictionSeverity>,
    ): SelfProfileSetupResult
}

interface ExistingSelfProfileResolver {
    suspend fun resolveActiveSelfProfileId(): Long
}

class FamilyExistingSelfProfileResolver @Inject constructor(
    private val familyProfileRepository: FamilyProfileRepository,
) : ExistingSelfProfileResolver {
    override suspend fun resolveActiveSelfProfileId(): Long {
        val family = familyProfileRepository.getMyFamily()
        if (family != null) {
            return requirePositiveProfileId(family.selfProfileId)
        }
        val activeProfile = familyProfileRepository.getActiveProfile()
        check(activeProfile.relationship.equals("SELF", ignoreCase = true)) {
            "Active profile is not the caller's SELF profile."
        }
        return requirePositiveProfileId(activeProfile.profileId)
    }

    private fun requirePositiveProfileId(profileId: Long): Long {
        check(profileId > 0) { "Resolved SELF profile id must be positive." }
        return profileId
    }
}

class ServerSelfProfileRepository @Inject constructor(
    private val apiService: SelfProfileApiService,
) : SelfProfileRepository {
    override suspend fun createSelfProfile(
        profileName: String,
        restrictions: Map<Long, ProfileRestrictionSeverity>,
    ): SelfProfileSetupResult {
        return try {
            val response = apiService.createSelfProfile(
                CreateSelfProfileRequest(
                    profileName = profileName.trim(),
                    restrictions = restrictions,
                ),
            )
            val body = response.body()
            when {
                response.code() == HTTP_CREATED && body?.profileId?.let { it > 0 } == true ->
                    SelfProfileSetupResult.Created(body)
                response.code() in 200..299 -> SelfProfileSetupResult.Failure(INVALID_RESPONSE_MESSAGE)
                response.code() == 400 -> SelfProfileSetupResult.InvalidRequest(
                    "Check the profile name and dietary selections and try again.",
                )
                response.code() == 401 -> SelfProfileSetupResult.Unauthenticated
                response.code() == 403 -> SelfProfileSetupResult.Forbidden
                response.code() == 409 -> SelfProfileSetupResult.AlreadyExists
                else -> SelfProfileSetupResult.Failure(
                    "Dietary profile setup is temporarily unavailable. Try again later.",
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            SelfProfileSetupResult.Failure(
                "Check your connection and try dietary profile setup again.",
            )
        }
    }

    private companion object {
        const val HTTP_CREATED = 201
        const val INVALID_RESPONSE_MESSAGE =
            "Dietary profile setup returned an invalid response. Try again later."
    }
}
