package sg.edu.nus.iss.canmakan.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import javax.inject.Inject

// ViewModel was created for CanMakanNavGraph solely to access ActiveProfileManager
// Composables cannot access Singleton directly, need to go through ViewModel
@HiltViewModel
class CanMakanNavGraphViewModel @Inject constructor (
    private val activeProfileManager: ActiveProfileManager): ViewModel() {

        val currentProfileId: StateFlow<Long> = activeProfileManager.currentProfileId

    fun switchProfile(profileId: Long) {
        activeProfileManager.switchProfile(profileId)
    }
}