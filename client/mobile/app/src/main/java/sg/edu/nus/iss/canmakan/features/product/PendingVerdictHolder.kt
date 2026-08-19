package sg.edu.nus.iss.canmakan.features.product

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
import javax.inject.Inject

class PendingVerdictHolder @Inject constructor() {
    private val _pendingVerdict = MutableStateFlow<VerdictDetail?>(null)
    val pendingVerdict: StateFlow<VerdictDetail?> = _pendingVerdict.asStateFlow()

    fun set(detail: VerdictDetail) {
        _pendingVerdict.value = detail
    }

    fun clear() {
        _pendingVerdict.value = null
    }
}
