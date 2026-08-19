package sg.edu.nus.iss.canmakan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationStore
import sg.edu.nus.iss.canmakan.navigation.CanMakanApp
import sg.edu.nus.iss.canmakan.shared.ui.theme.CanMakanTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var pendingInvitationStore: PendingInvitationStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureInviteToken(intent)
        enableEdgeToEdge()
        setContent {
            CanMakanTheme {
                CanMakanApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureInviteToken(intent)
    }

    private fun captureInviteToken(intent: Intent?) {
        val token = extractInviteToken(intent?.data) ?: return
        pendingInvitationStore.offer(token)
    }

    companion object {
        fun extractInviteToken(uri: Uri?): String? {
            if (uri == null) return null
            val path = uri.path.orEmpty()
            // canmakan://invite/{token}  → host=invite, path=/{token}
            // https://{CANMAKAN_INVITES_PUBLIC_BASE_URL host}/invite/{token} → path=/invite/{token}
            return when {
                uri.scheme.equals("canmakan", ignoreCase = true) &&
                    uri.host.equals("invite", ignoreCase = true) -> {
                    path.trim('/').takeIf { it.isNotBlank() }
                }
                path.startsWith("/invite/") -> {
                    path.removePrefix("/invite/").trim('/').takeIf { it.isNotBlank() }
                }
                else -> null
            }
        }
    }
}
