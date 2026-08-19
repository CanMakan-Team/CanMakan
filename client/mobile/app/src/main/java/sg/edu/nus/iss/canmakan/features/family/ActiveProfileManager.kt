package sg.edu.nus.iss.canmakan.features.family

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveProfileManager @Inject constructor() {
    data class Selection(
        val accountKey: AuthAccountKey,
        val profileId: Long,
    ) {
        val accountId: Long get() = accountKey.userId
    }

    private val _selection = MutableStateFlow<Selection?>(null)
    val selection: StateFlow<Selection?> = _selection

    private val _currentProfileId = MutableStateFlow(UNSET_PROFILE_ID)
    val currentProfileId: StateFlow<Long> = _currentProfileId

    @Synchronized
    fun switchProfile(accountKey: AuthAccountKey, profileId: Long) {
        require(accountKey.userId > 0) { "Authenticated account id must be positive." }
        require(accountKey.sessionGeneration > 0) {
            "Authenticated session generation must be positive."
        }
        require(profileId > 0) { "Active profile id must be positive." }
        _selection.value = Selection(accountKey = accountKey, profileId = profileId)
        _currentProfileId.value = profileId
    }

    @Synchronized
    fun reset() {
        _selection.value = null
        _currentProfileId.value = UNSET_PROFILE_ID
    }

    /** A stale operation may only clear the selection it owns, never a newer account's. */
    @Synchronized
    fun resetForOwner(accountKey: AuthAccountKey) {
        if (_selection.value?.accountKey == accountKey) reset()
    }

    fun isCurrent(accountKey: AuthAccountKey, profileId: Long): Boolean =
        selection.value == Selection(accountKey = accountKey, profileId = profileId)

    companion object {
        /** No profile resolved yet; replaced after authenticated profile setup or GET active-profile. */
        const val UNSET_PROFILE_ID = 0L
    }
}
