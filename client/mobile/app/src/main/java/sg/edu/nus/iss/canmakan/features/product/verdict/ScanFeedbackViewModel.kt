package sg.edu.nus.iss.canmakan.features.product.verdict

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.shared.network.CanMakanApiService
import sg.edu.nus.iss.canmakan.shared.network.ScanFeedbackRequest
import javax.inject.Inject

/*
 * State of a thumbs-down report submission (UC20). Thumbs up has no form to
 * show progress/errors in, so it doesn't use this state — see
 * submitPositiveFeedback.
 * IDLE: nothing submitted yet for this scan.
 * SUBMITTING: the report is in flight.
 * SUBMITTED: the report was saved.
 * ERROR: the report failed to save; errorMessage explains why.
 *
 * author Kwok Heng
 */
enum class FeedbackSubmissionState {
    IDLE,
    SUBMITTING,
    SUBMITTED,
    ERROR
}

@HiltViewModel
class ScanFeedbackViewModel @Inject constructor(
    private val apiService: CanMakanApiService,
) : ViewModel() {

    private val _submissionState = MutableStateFlow(FeedbackSubmissionState.IDLE)
    val submissionState: StateFlow<FeedbackSubmissionState> = _submissionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Reports [scanId]'s verdict as incorrect. [comment] is optional free text;
    // blank/null is still a valid submission (a bare thumbs down). Drives the
    // comment box's submitting/submitted/error UI via [submissionState].
    fun submitNegativeFeedback(scanId: Long, comment: String?) {
        if (_submissionState.value == FeedbackSubmissionState.SUBMITTING) return
        _submissionState.value = FeedbackSubmissionState.SUBMITTING
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val trimmedComment = comment?.trim()?.takeIf { it.isNotEmpty() }
                val response = apiService.submitScanFeedback(
                    scanId = scanId,
                    request = ScanFeedbackRequest(isPositive = false, userComments = trimmedComment),
                )
                if (response.isSuccessful) {
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

    // Logs a thumbs up for [scanId]. Fire-and-forget: a thumbs up has no
    // comment and no visible form, so it doesn't touch [submissionState] —
    // the confetti celebration plays regardless, and a failure here is not
    // worth interrupting the user over.
    fun submitPositiveFeedback(scanId: Long) {
        viewModelScope.launch {
            try {
                apiService.submitScanFeedback(
                    scanId = scanId,
                    request = ScanFeedbackRequest(isPositive = true, userComments = null),
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // Best-effort logging only; nothing for the user to retry here.
            }
        }
    }
}
