package sg.edu.nus.iss.canmakan.features.account

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.CardWhite
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextPrimary
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

// Settings screen reached from the profile drawer. Shows the notification
// toggle for the app and an account deletion option, guarded by a
// confirmation dialog since deleting an account cannot be undone.
@Composable
fun SettingsScreen(
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    hasUnreadNotifications: Boolean = false,
    onBackClick: () -> Unit,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    notificationsEnabledError: String? = null,
    isDeletingAccount: Boolean = false,
    deleteAccountError: String? = null,
    onConfirmDeleteAccount: () -> Unit
) {
    // Controls whether the delete confirmation dialog is visible.
    var showDeleteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    // Requests the OS permission to post system notifications, required from API 33
    // onward. The toggle is saved either way -- SystemNotifier re-checks this permission
    // before every post, so a grant made later from Android's own app settings takes
    // effect without the user needing to flip this switch again.
    val requestPostNotificationsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Result intentionally ignored; see comment above. */ }

    Scaffold(
        topBar = {
            AppTopBar(
                onMenuClick = onMenuClick,
                onNotificationsClick = onNotificationsClick,
                hasUnreadNotifications = hasUnreadNotifications,
            )
        },
        bottomBar = {
            AppBottomNavBar(
                selectedTab = BottomTab.SCAN,
                onScanClick = onScanClick,
                onHistoryClick = onHistoryClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {
            // Row with the back arrow and the screen title, matching the
            // other screens reached from the drawer (e.g. Manage Family,
            // Notifications).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onBackClick)
                    .padding(bottom = 24.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back")
            }

            Text(
                text = "Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Manage your notification preferences and account.",
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Notification toggle row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Allow CanMakan to send notifications",
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasPostNotificationsPermission(context)) {
                            requestPostNotificationsPermission.launch(
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        }
                        onNotificationsEnabledChanged(enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = PrimaryGreen
                    )
                )
            }
            if (notificationsEnabledError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = notificationsEnabledError,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Pushes the delete button down toward the bottom of the screen,
            // leaving empty space above it to match the prototype layout.
            Spacer(modifier = Modifier.weight(1f))

            if (deleteAccountError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = deleteAccountError,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { showDeleteDialog = true },
                enabled = !isDeletingAccount,
                colors = ButtonDefaults.buttonColors(containerColor = AvoidRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isDeletingAccount) "Deleting…" else "Delete My Account",
                    color = CardWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Confirmation dialog shown before an account is actually deleted.
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete account?") },
            text = {
                Text(
                    "This action cannot be undone. You will no longer be able to sign in. ",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onConfirmDeleteAccount()
                    },
                    enabled = !isDeletingAccount,
                ) {
                    Text("Delete", color = AvoidRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// The POST_NOTIFICATIONS permission only exists from API 33 onward; earlier versions
// show notifications for any app that posts them, so there is nothing to check.
private fun hasPostNotificationsPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}
