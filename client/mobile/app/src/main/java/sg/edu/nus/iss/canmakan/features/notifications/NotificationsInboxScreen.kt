package sg.edu.nus.iss.canmakan.features.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sg.edu.nus.iss.canmakan.features.family.data.PendingInvitationResponse
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.ActiveProfileChip
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotEmptyState
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotPose
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

/**
 * Account-wide notifications inbox (top-bar bell).
 * Currently lists family invitations; other notice types can share this screen later.
 */
@Composable
fun NotificationsInboxScreen(
    activeProfile: DietaryProfile?,
    hasFamily: Boolean = false,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onAccepted: () -> Unit = {},
    viewModel: NotificationsInboxViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hasInvitations = uiState.invitations.isNotEmpty()
    val subtitle = when {
        !hasInvitations && uiState.errorMessage == null && !uiState.isLoading ->
            "No notifications yet"
        hasFamily -> "Updates and alerts for your family"
        else -> "Updates and alerts for your account"
    }

    Scaffold(
        topBar = {
            Column {
                AppTopBar(
                    onMenuClick = onMenuClick,
                    onNotificationsClick = onNotificationsClick,
                )
                activeProfile?.let { ActiveProfileChip(profile = it) }
            }
        },
        bottomBar = {
            AppBottomNavBar(
                selectedTab = BottomTab.SCAN,
                onScanClick = onScanClick,
                onHistoryClick = onHistoryClick,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onBackClick() }
                        .padding(bottom = 12.dp),
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back")
                }

                Text(
                    text = "Notifications",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                )
            }

            uiState.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.clearError() },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::refresh) {
                        Text("Retry")
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                hasInvitations -> {
                    items(
                        items = uiState.invitations,
                        key = { it.invitationId },
                    ) { invitation ->
                        FamilyInvitationNotificationCard(
                            invitation = invitation,
                            isActing = uiState.actingToken == invitation.invitationToken,
                            onAccept = {
                                viewModel.accept(invitation.invitationToken, onAccepted)
                            },
                            onDecline = {
                                viewModel.decline(invitation.invitationToken)
                            },
                        )
                        HorizontalDivider()
                    }
                }

                else -> {
                    item {
                        CanMakanMascotEmptyState(
                            title = "No notifications yet",
                            body = "Family invitations and updates will show up here.",
                            pose = CanMakanMascotPose.Wave,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp, bottom = 24.dp),
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun FamilyInvitationNotificationCard(
    invitation: PendingInvitationResponse,
    isActing: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = invitation.familyName,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Invited by ${invitation.invitedByDisplayName}",
            color = TextSecondary,
        )
        invitation.expiresAt?.let { expiresAt ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (invitation.expired) {
                    "Expired ($expiresAt)"
                } else {
                    "Expires $expiresAt"
                },
                color = if (invitation.expired) {
                    MaterialTheme.colorScheme.error
                } else {
                    TextSecondary
                },
                fontSize = 12.sp,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onAccept,
                enabled = !invitation.expired && !isActing,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isActing) "Working…" else "Accept")
            }
            OutlinedButton(
                onClick = onDecline,
                enabled = !isActing,
                modifier = Modifier.weight(1f),
            ) {
                Text("Decline")
            }
        }
    }
}
