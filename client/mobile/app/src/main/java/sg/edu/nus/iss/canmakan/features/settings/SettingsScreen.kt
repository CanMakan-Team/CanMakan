package sg.edu.nus.iss.canmakan.features.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onConfirmDeleteAccount: () -> Unit
) {
    // Holds whether notifications are currently allowed. In a full
    // implementation this would be loaded from and saved to a settings
    // repository instead of starting as a fixed default.
    var notificationsEnabled by remember { mutableStateOf(true) }

    // Controls whether the delete confirmation dialog is visible.
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                    onCheckedChange = { notificationsEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = PrimaryGreen
                    )
                )
            }

            // Pushes the delete button down toward the bottom of the screen,
            // leaving empty space above it to match the prototype layout.
            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { showDeleteDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = AvoidRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Delete My Account",
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
                Text("This action cannot be undone. All scan history and saved profiles will be permanently removed.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onConfirmDeleteAccount()
                    }
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
