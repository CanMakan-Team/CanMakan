package sg.edu.nus.iss.canmakan.features.family.data

import com.google.gson.Gson
import retrofit2.HttpException
import retrofit2.Response
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import javax.inject.Inject

class FamilyProfileRepository @Inject constructor(
    private val apiService: FamilyProfileApiService,
    private val gson: Gson = Gson(),
) {
    /**
     * Returns the caller's family context, or null when the user has no membership (HTTP 404).
     * Identity comes from the Bearer token attached by the auth interceptor.
     */
    suspend fun getMyFamily(): FamilyMeResponse? {
        val response = apiService.getMyFamily()
        if (response.code() == 404) {
            return null
        }
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        return response.body()
            ?: throw IllegalStateException("Empty body for GET /families/me")
    }

    /**
     * UC12 roster of linked members and dependants. Empty when the caller has no family.
     */
    suspend fun getFamilyMembers(): List<FamilyMemberRosterItem> {
        val response = apiService.getFamilyMembers()
        if (response.code() == 404) {
            return emptyList()
        }
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        return response.body().orEmpty()
    }

    /**
     * Creates a family circle. HTTP 409 (already a member) reloads `/me` instead of failing.
     */
    suspend fun createFamily(familyName: String): FamilyMeResponse {
        val response = apiService.createFamily(
            request = CreateFamilyRequestBody(familyName = familyName),
        )
        if (response.code() == 409) {
            return getMyFamily()
                ?: throw IllegalStateException("Family already exists but GET /families/me returned 404")
        }
        if (!response.isSuccessful) {
            throw FamilyApiException(messageFromError(response), response.code())
        }
        return response.body()
            ?: throw IllegalStateException("Empty body for POST /families")
    }

    suspend fun getProfilesForFamily(familyId: Long): List<DietaryProfile> {
        return FamilyProfileMapper.fromResponses(apiService.getProfilesByFamilyId(familyId))
    }

    suspend fun getActiveProfile(): ActiveProfileResponse {
        val response = apiService.getActiveProfile()
        if (!response.isSuccessful) {
            throw FamilyApiException(messageFromError(response), response.code())
        }
        return response.body()
            ?: throw IllegalStateException("Empty body for GET /families/me/active-profile")
    }

    suspend fun setActiveProfile(profileId: Long): ActiveProfileResponse {
        require(profileId > 0) { "Active profile id must be positive." }
        val response = apiService.setActiveProfile(
            SetActiveProfileRequestBody(profileId = profileId),
        )
        if (!response.isSuccessful) {
            throw FamilyApiException(messageFromError(response), response.code())
        }
        return response.body()
            ?: throw IllegalStateException("Empty body for PUT /families/me/active-profile")
    }

    suspend fun getNotificationPreference(): Boolean {
        val response = apiService.getNotificationPreference()
        if (!response.isSuccessful) {
            throw FamilyApiException(messageFromError(response), response.code())
        }
        return response.body()?.notificationsEnabled
            ?: throw IllegalStateException("Empty body for GET /users/me/preferences/notifications")
    }

    suspend fun setNotificationPreference(enabled: Boolean): Boolean {
        val response = apiService.setNotificationPreference(
            SetNotificationPreferenceRequestBody(notificationsEnabled = enabled),
        )
        if (!response.isSuccessful) {
            throw FamilyApiException(messageFromError(response), response.code())
        }
        return response.body()?.notificationsEnabled
            ?: throw IllegalStateException("Empty body for PUT /users/me/preferences/notifications")
    }

    private fun messageFromError(response: Response<*>): String {
        val raw = response.errorBody()?.string().orEmpty()
        if (raw.isBlank()) {
            return "Could not create family circle."
        }
        val extracted = runCatching {
            gson.fromJson(raw, ApiErrorBody::class.java)?.message
        }.getOrNull().orEmpty()
        return extracted.ifBlank { "Could not create family circle." }
    }

    /** (UC6) View Family Allergy Summary Grid */
    suspend fun getFamilyRestrictionSummary(): FamilyRestrictionSumRes {
        val response = apiService.getFamilyRestrictionSummary()
        if (!response.isSuccessful) {
            throw FamilyApiException(messageFromError(response), response.code())
        }
        return response.body()
            ?: throw IllegalStateException("Empty body for GET /families/me/restriction-summary")
    }

    private data class ApiErrorBody(val message: String?)

    suspend fun searchUserByEmail(email: String): UserSearchResponse {
        val response = apiService.searchUserByEmail(email.trim().lowercase())
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        return response.body()
            ?: throw IllegalStateException("Empty body for GET /families/me/user-search")
    }

    suspend fun createInvitation(email: String, relationship: String): InvitationResponse {
        val response = apiService.createInvitation(
            CreateInvitationRequestBody(
                email = email.trim().lowercase(),
                relationship = relationship.trim().uppercase(),
            ),
        )
        if (!response.isSuccessful) {
            throw FamilyApiException(messageFromError(response), response.code())
        }
        return response.body()
            ?: throw IllegalStateException("Empty body for POST /families/me/invitations")
    }

    suspend fun claimInvitation(invitationToken: String): FamilyMeResponse {
        val response = apiService.claimInvitation(
            ClaimInvitationRequestBody(invitationToken = invitationToken.trim()),
        )
        if (!response.isSuccessful) {
            throw FamilyApiException(messageFromError(response), response.code())
        }
        return response.body()
            ?: throw IllegalStateException("Empty body for POST /families/me/invitations/claim")
    }

    suspend fun listMyInvitations(): List<PendingInvitationResponse> {
        val response = apiService.listMyInvitations()
        if (!response.isSuccessful) {
            throw FamilyApiException(messageFromError(response), response.code())
        }
        return response.body().orEmpty()
    }

    suspend fun acceptInvitation(invitationToken: String): FamilyMeResponse {
        val response = apiService.acceptInvitation(invitationToken.trim())
        if (!response.isSuccessful) {
            throw FamilyApiException(messageFromError(response), response.code())
        }
        return response.body()
            ?: throw IllegalStateException("Empty body for POST /invitations/{token}/accept")
    }

    suspend fun declineInvitation(invitationToken: String) {
        val response = apiService.declineInvitation(invitationToken.trim())
        if (!response.isSuccessful) {
            throw FamilyApiException(messageFromError(response), response.code())
        }
    }

    suspend fun createDependantProfile(
        profileName: String,
        relationship: String,
        commonRequirements: List<String> = emptyList(),
        restrictions: List<String> = emptyList(),
    ): DependantProfileResponse {
        val response = apiService.createDependantProfile(
            CreateDependantProfileRequestBody(
                profileName = profileName.trim(),
                relationship = relationship.trim(),
                commonRequirements = commonRequirements,
                restrictions = restrictions,
            ),
        )
        if (!response.isSuccessful) {
            throw FamilyApiException(messageFromError(response), response.code())
        }
        return response.body()
            ?: throw IllegalStateException("Empty body for POST /families/me/profiles")
    }
}
