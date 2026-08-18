package sg.edu.nus.iss.canmakan.features.family

import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey
import sg.edu.nus.iss.canmakan.features.family.data.FamilyApiException
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileMapper
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import javax.inject.Inject

data class FamilyShellSnapshot(
    val hasFamily: Boolean,
    val familyName: String?,
    val showManageFamilyActions: Boolean,
    val selfProfileId: Long?,
    val memberRole: String?,
    val profiles: List<DietaryProfile>,
    val resolvedProfileId: Long?,
) {
    companion object {
        fun empty(): FamilyShellSnapshot = FamilyShellSnapshot(
            hasFamily = false,
            familyName = null,
            showManageFamilyActions = false,
            selfProfileId = null,
            memberRole = null,
            profiles = emptyList(),
            resolvedProfileId = null,
        )
    }
}

class FamilyContextLoader @Inject constructor(
    private val familyProfileRepository: FamilyProfileRepository,
    private val activeProfileManager: ActiveProfileManager,
) {
    suspend fun load(accountKey: AuthAccountKey, isCurrentAccount: () -> Boolean): FamilyShellSnapshot? {
        val me = familyProfileRepository.getMyFamily()
        if (!isCurrentAccount()) return null

        val loadedProfiles = if (me != null) {
            familyProfileRepository.getProfilesForFamily(me.familyId)
        } else {
            emptyList()
        }
        if (!isCurrentAccount()) return null

        val activeFromServer = try {
            familyProfileRepository.getActiveProfile()
        } catch (exception: FamilyApiException) {
            if (exception.statusCode == 404) null else throw exception
        }
        if (!isCurrentAccount()) return null

        if (activeFromServer == null) {
            activeProfileManager.selection.value
                ?.takeIf { it.accountKey == accountKey }
                ?.let { activeProfileManager.reset() }
            return FamilyShellSnapshot(
                hasFamily = me != null,
                familyName = me?.familyName,
                showManageFamilyActions = me?.memberRole == "PRIMARY_ADMIN",
                selfProfileId = me?.selfProfileId,
                memberRole = me?.memberRole,
                profiles = emptyList(),
                resolvedProfileId = null,
            )
        }

        require(activeFromServer.profileId > 0) {
            "Active-profile response must contain a positive profile id."
        }
        val resolvedProfileId = if (me == null) {
            activeFromServer.profileId
        } else {
            resolveActiveProfileId(
                serverProfileId = activeFromServer.profileId,
                loadedProfiles = loadedProfiles,
                selfProfileId = me.selfProfileId,
            )
        }
        require(resolvedProfileId > 0) { "Resolved active profile id must be positive." }

        val activeProfile = FamilyProfileMapper.fromActiveResponse(activeFromServer)
        val profiles = if (me == null) {
            listOf(activeProfile)
        } else {
            loadedProfiles.ifEmpty { listOf(activeProfile) }
        }
        return FamilyShellSnapshot(
            hasFamily = me != null,
            familyName = me?.familyName,
            showManageFamilyActions = me?.memberRole == "PRIMARY_ADMIN",
            selfProfileId = me?.selfProfileId,
            memberRole = me?.memberRole,
            profiles = profiles,
            resolvedProfileId = resolvedProfileId,
        )
    }

    private fun resolveActiveProfileId(
        serverProfileId: Long,
        loadedProfiles: List<DietaryProfile>,
        selfProfileId: Long?,
    ): Long {
        if (loadedProfiles.any { it.id == serverProfileId }) return serverProfileId
        if (selfProfileId != null && loadedProfiles.any { it.id == selfProfileId }) {
            return selfProfileId
        }
        return loadedProfiles.firstOrNull()?.id ?: serverProfileId
    }
}
