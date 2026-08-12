package sg.edu.nus.iss.canmakan.features.family.ui

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
import androidx.compose.ui.graphics.Color
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

@Composable
fun InvitationsScreen(
    activeProfile: DietaryProfile,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onAccepted: () -> Unit = {},
    viewModel: InvitationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                AppTopBar(
                    onMenuClick = onMenuClick,
                    onNotificationsClick = onNotificationsClick,
                )
                ActiveProfileChip(profile = activeProfile)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onBackClick() }
                    .padding(bottom = 24.dp),
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
                text = "Accept or decline pending family invitations.",
                color = Color.Gray,
            )

            Spacer(modifier = Modifier.height(16.dp))

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.clearError() }
                        .padding(bottom = 12.dp),
                )
                OutlinedButton(onClick = viewModel::refresh) {
                    Text("Retry")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.invitations.isEmpty() && uiState.errorMessage == null -> {
                    Text(
                        text = "No pending invitations.",
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = uiState.invitations,
                            key = { it.invitationId },
                        ) { invitation ->
                            InvitationCard(
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
                }
            }
        }
    }
}

@Composable
private fun InvitationCard(
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
            color = Color.Gray,
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
                    Color.Gray
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
