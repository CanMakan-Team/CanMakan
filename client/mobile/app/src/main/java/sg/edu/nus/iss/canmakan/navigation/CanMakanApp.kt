package sg.edu.nus.iss.canmakan.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sg.edu.nus.iss.canmakan.features.auth.AppAuthState
import sg.edu.nus.iss.canmakan.features.auth.AppAuthViewModel

/** Root application composition. Auth and main NavControllers never share a back stack. */
@Composable
fun CanMakanApp(
    authViewModel: AppAuthViewModel = hiltViewModel(),
) {
    val state by authViewModel.state.collectAsStateWithLifecycle()

    when (state) {
        AppAuthState.Restoring -> AuthStatusScreen(
            title = "Restoring your session",
            message = "Checking your secure CanMakan session.",
            showProgress = true,
        )

        AppAuthState.SigningOut -> AuthStatusScreen(
            title = "Signing out",
            message = "Securing this device and ending your session.",
            showProgress = true,
        )

        AppAuthState.Unauthenticated -> key("auth-flow") {
            AuthNavGraph(onLoginSuccess = authViewModel::onLoginSuccess)
        }

        is AppAuthState.Authenticated -> key("main-flow") {
            CanMakanNavGraph(onSignOut = authViewModel::signOut)
        }

        is AppAuthState.UnsupportedMobileAccount -> AuthStatusScreen(
            title = "Account not supported",
            message = "This account is not supported in the CanMakan mobile app.",
            primaryActionLabel = "Sign Out",
            onPrimaryAction = authViewModel::signOut,
        )

        AppAuthState.TemporarilyUnavailable -> AuthStatusScreen(
            title = "CanMakan is temporarily unavailable",
            message = "Your saved session has not been removed. Check your connection and try again.",
            primaryActionLabel = "Retry",
            onPrimaryAction = authViewModel::retryRestoration,
            secondaryActionLabel = "Sign Out",
            onSecondaryAction = authViewModel::signOut,
        )

        AppAuthState.Forbidden -> AuthStatusScreen(
            title = "Access unavailable",
            message = "This account cannot currently access CanMakan.",
            primaryActionLabel = "Retry",
            onPrimaryAction = authViewModel::retryRestoration,
            secondaryActionLabel = "Sign Out",
            onSecondaryAction = authViewModel::signOut,
        )
    }
}

@Composable
private fun AuthStatusScreen(
    title: String,
    message: String,
    showProgress: Boolean = false,
    primaryActionLabel: String? = null,
    onPrimaryAction: () -> Unit = {},
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showProgress) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(24.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            primaryActionLabel?.let { label ->
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onPrimaryAction) {
                    Text(label)
                }
            }
            secondaryActionLabel?.let { label ->
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onSecondaryAction) {
                    Text(label)
                }
            }
        }
    }
}
