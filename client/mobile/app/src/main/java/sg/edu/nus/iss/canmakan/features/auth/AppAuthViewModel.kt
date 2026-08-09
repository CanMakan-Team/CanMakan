package sg.edu.nus.iss.canmakan.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sg.edu.nus.iss.canmakan.features.auth.data.AuthRole
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.session.AuthLogoutAction
import sg.edu.nus.iss.canmakan.features.auth.session.AuthRestorationResult
import sg.edu.nus.iss.canmakan.features.auth.session.AuthRestorer
import sg.edu.nus.iss.canmakan.features.auth.session.AuthSessionStore

/** Token-free application authentication state. */
sealed interface AppAuthState {
    data object Restoring : AppAuthState

    data object Unauthenticated : AppAuthState

    data class Authenticated(val user: AuthenticatedUser) : AppAuthState

    data class UnsupportedMobileAccount(val user: AuthenticatedUser) : AppAuthState

    data object SigningOut : AppAuthState

    data object TemporarilyUnavailable : AppAuthState

    data object Forbidden : AppAuthState
}

@HiltViewModel
class AppAuthViewModel @Inject constructor(
    private val authRestorer: AuthRestorer,
    private val authSessionStore: AuthSessionStore,
    private val logoutAction: AuthLogoutAction,
    @AuthIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<AppAuthState>(AppAuthState.Restoring)
    val state: StateFlow<AppAuthState> = _state.asStateFlow()

    private var restorationValidated = false
    private var restoreJob: Job? = null
    private var logoutJob: Job? = null

    init {
        observeSafeSession()
        restoreSession()
    }

    fun retryRestoration() {
        restoreSession()
    }

    fun onLoginSuccess(user: AuthenticatedUser) {
        if (_state.value is AppAuthState.SigningOut) return
        restorationValidated = true
        _state.value = stateFor(user)
    }

    fun signOut() {
        if (_state.value is AppAuthState.Unauthenticated ||
            _state.value is AppAuthState.SigningOut ||
            logoutJob?.isActive == true
        ) {
            return
        }

        restoreJob?.cancel()
        restorationValidated = true
        _state.value = AppAuthState.SigningOut
        logoutJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                logoutAction.logout()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // The action owns unconditional local cleanup; UI never exposes raw failures.
            } finally {
                _state.value = AppAuthState.Unauthenticated
            }
        }
    }

    override fun toString(): String = "AppAuthViewModel(state=${state.value})"

    private fun restoreSession() {
        if (restoreJob?.isActive == true || logoutJob?.isActive == true) return

        restorationValidated = false
        _state.value = AppAuthState.Restoring
        restoreJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val result = try {
                withContext(ioDispatcher) { authRestorer.restore() }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                AuthRestorationResult.TemporarilyUnavailable
            }

            restorationValidated = true
            _state.value = when (result) {
                is AuthRestorationResult.Authenticated -> stateFor(result.user)
                AuthRestorationResult.Unauthenticated -> AppAuthState.Unauthenticated
                AuthRestorationResult.TemporarilyUnavailable ->
                    AppAuthState.TemporarilyUnavailable

                AuthRestorationResult.Forbidden -> AppAuthState.Forbidden
            }
        }
    }

    private fun observeSafeSession() {
        viewModelScope.launch {
            authSessionStore.authenticatedUser.collect { user ->
                val currentState = _state.value
                if (!restorationValidated ||
                    currentState is AppAuthState.Restoring ||
                    currentState is AppAuthState.SigningOut
                ) {
                    return@collect
                }

                if (user == null) {
                    _state.value = AppAuthState.Unauthenticated
                } else if (currentState is AppAuthState.Authenticated ||
                    currentState is AppAuthState.UnsupportedMobileAccount ||
                    currentState is AppAuthState.Unauthenticated
                ) {
                    _state.value = stateFor(user)
                }
            }
        }
    }

    private fun stateFor(user: AuthenticatedUser): AppAuthState {
        return when (user.role) {
            AuthRole.USER -> AppAuthState.Authenticated(user)
            AuthRole.ADMIN -> AppAuthState.UnsupportedMobileAccount(user)
        }
    }
}
