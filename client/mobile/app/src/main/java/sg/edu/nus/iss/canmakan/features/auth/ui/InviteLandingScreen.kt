package sg.edu.nus.iss.canmakan.features.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextPrimary
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

/** Unauthenticated invite landing: register or sign in with the invite token preserved. */
@Composable
fun InviteLandingScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Family invitation",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
        )
        Text(
            text = "You are invited to join a CanMakan family.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Text(
            text = "Create an account with the invited email, or sign in if you already have one. " +
                "The invitation is claimed automatically after authentication.",
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create account")
        }
        OutlinedButton(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign in")
        }
    }
}
