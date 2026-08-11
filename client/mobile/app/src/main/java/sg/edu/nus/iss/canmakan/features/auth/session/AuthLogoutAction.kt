package sg.edu.nus.iss.canmakan.features.auth.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import sg.edu.nus.iss.canmakan.features.auth.AuthIoDispatcher

/** Application-facing logout action that never exposes a credential or backend response. */
fun interface AuthLogoutAction {
    suspend fun logout()
}

/** Keeps the thread-affine refresh/logout lock entirely inside one synchronous IO operation. */
@Singleton
class SerializedAuthLogoutAction @Inject constructor(
    private val refreshCoordinator: AuthRefreshCoordinator,
    @AuthIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthLogoutAction {
    override suspend fun logout() {
        withContext(NonCancellable + ioDispatcher) {
            refreshCoordinator.logout()
        }
    }

    override fun toString(): String = "SerializedAuthLogoutAction(state=<redacted>)"
}
