package sg.edu.nus.iss.canmakan.features.product.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.shared.network.CanMakanApiService
import sg.edu.nus.iss.canmakan.shared.network.ScanRequest
import javax.inject.Inject

enum class ValidationState {
    IDLE, VALIDATING, VALID, INVALID, ERROR
}
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val apiService: CanMakanApiService
) : ViewModel() {
    private val _validationState = MutableStateFlow(ValidationState.IDLE)
    val validationState: StateFlow<ValidationState> = _validationState

    fun processBarcode(barcode: String) {
        _validationState.value = ValidationState.VALIDATING

        viewModelScope.launch {
            try {
                val response = apiService.validateBarcode(ScanRequest(barcode))
                if (response.isSuccessful && response.body() != null) {
                    val isValid = response.body()!!.validFood
                    _validationState.value = if (isValid) {
                        ValidationState.VALID
                    } else {
                        ValidationState.INVALID
                    }
                } else {
                    _validationState.value = ValidationState.ERROR
                }
            } catch (e: Exception) {
                _validationState.value = ValidationState.ERROR
            }
        }
    }

    fun resetState() {
        _validationState.value = ValidationState.IDLE
    }
}