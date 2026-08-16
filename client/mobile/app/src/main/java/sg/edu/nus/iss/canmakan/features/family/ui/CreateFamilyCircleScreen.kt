package sg.edu.nus.iss.canmakan.features.family.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.edu.nus.iss.canmakan.navigation.InviteWebDeepLinks
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

/**
 * UC8: create a family circle when the user has none.
 * Only shown when membership is missing; requires a persisted user session.
 */
@Composable
fun CreateFamilyCircleScreen(
    isSubmitting: Boolean,
    errorMessage: String?,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    hasUnreadNotifications: Boolean = false,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBackClick: () -> Unit,
    onCreateClick: (familyName: String) -> Unit,
) {
    var familyName by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var portalLinkCopied by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val familyPortalUrl = remember { InviteWebDeepLinks.familyPortalMembersUrl() }

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
                onHistoryClick = onHistoryClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(enabled = !isSubmitting, onClick = onBackClick)
                    .padding(bottom = 24.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back")
            }

            Text(
                text = "Create family circle",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Manage your family's dietary profiles from this account.",
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Family name", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = familyName,
                onValueChange = {
                    familyName = it
                    validationError = null
                },
                placeholder = { Text("e.g. Wong Family") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSubmitting,
                isError = validationError != null || errorMessage != null,
                supportingText = {
                    val message = validationError ?: errorMessage
                    if (message != null) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val trimmed = familyName.trim()
                    when {
                        trimmed.isEmpty() -> validationError = "Family name is required."
                        trimmed.length > 100 ->
                            validationError = "Family name must be at most 100 characters."
                        else -> {
                            validationError = null
                            onCreateClick(trimmed)
                        }
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Create")
                }
            }

            if (familyPortalUrl != null) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Family Portal website",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = familyPortalUrl,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(familyPortalUrl))
                        portalLinkCopied = true
                    },
                ) {
                    Text(if (portalLinkCopied) "Copied" else "Copy URL")
                }
            }
        }
    }
}
