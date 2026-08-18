package sg.edu.nus.iss.canmakan.features.product.verdict

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.product.scan.data.ScanRepository
import javax.inject.Inject

enum class FeedbackSubmissionState {
    IDLE,
    SUBMITTING,
    SUBMITTED,
    ERROR
}

@HiltViewModel
class ScanFeedbackViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
) : ViewModel() {

    private val _submissionState = MutableStateFlow(FeedbackSubmissionState.IDLE)
    val submissionState: StateFlow<FeedbackSubmissionState> = _submissionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun submitNegativeFeedback(scanId: Long, comment: String?) {
        if (_submissionState.value == FeedbackSubmissionState.SUBMITTING) return
        _submissionState.value = FeedbackSubmissionState.SUBMITTING
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val saved = scanRepository.submitFeedback(
                    scanId = scanId,
                    isPositive = false,
                    comment = comment,
                )
                if (saved) {
                    _submissionState.value = FeedbackSubmissionState.SUBMITTED
                } else {
                    _submissionState.value = FeedbackSubmissionState.ERROR
                    _errorMessage.value = "Could not submit feedback. Please try again."
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _submissionState.value = FeedbackSubmissionState.ERROR
                _errorMessage.value = exception.message ?: "Could not submit feedback. Please try again."
            }
        }
    }

    fun submitPositiveFeedback(scanId: Long) {
        viewModelScope.launch {
            try {
                scanRepository.submitFeedback(
                    scanId = scanId,
                    isPositive = true,
                    comment = null,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
            }
        }
    }
}
