package sg.edu.nus.iss.canmakan.features.family.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.family.data.CreateFamilyException
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository
import sg.edu.nus.iss.canmakan.features.family.model.RelationshipToAdmin

data class CreateNewProfileUiState(
    val profileName: String = "",
    val relationship: RelationshipToAdmin? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val created: Boolean = false,
)

@HiltViewModel
class CreateNewProfileViewModel @Inject constructor(
    private val familyProfileRepository: FamilyProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateNewProfileUiState())
    val uiState: StateFlow<CreateNewProfileUiState> = _uiState.asStateFlow()

    fun updateProfileName(value: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(profileName = value, errorMessage = null)
    }

    fun updateRelationship(value: RelationshipToAdmin) {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(relationship = value, errorMessage = null)
    }

    fun create() {
        val state = _uiState.value
        if (state.isSubmitting || state.created) return

        val name = state.profileName.trim()
        val relationship = state.relationship
        if (name.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Name is required.")
            return
        }
        if (relationship == null) {
            _uiState.value = state.copy(errorMessage = "Relationship is required.")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            try {
                familyProfileRepository.createDependantProfile(
                    profileName = name,
                    // enum name is already UPPERCASE, matching the dietary_profiles.relationship column.
                    relationship = relationship.name,
                )
                _uiState.value = _uiState.value.copy(isSubmitting = false, created = true)
            } catch (_: CreateFamilyException) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = "We are unable to save new family member. Please try again later.",
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = "We are unable to save new family member. Please try again later.",
                )
            }
        }
    }
}
