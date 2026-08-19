package sg.edu.nus.iss.canmakan.features.notifications

import android.util.Log
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sg.edu.nus.iss.canmakan.features.notifications.data.UserNotificationResponse
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotEmptyState
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotPose
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

/**
 * Account-wide notifications inbox (top-bar bell).
 * Family invite cards are one source; other features can write the same inbox.
 */
@Composable
fun NotificationsInboxScreen(
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    hasUnreadNotifications: Boolean = false,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onAccepted: () -> Unit = {},
    onMarkedAllRead: () -> Unit = {},
    viewModel: NotificationsInboxViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hasNotifications = uiState.notifications.isNotEmpty()
    val hasUnread = uiState.notifications.any { !it.read }
    val subtitle = when {
        !hasNotifications && uiState.errorMessage == null && !uiState.isLoading ->
            "No notifications yet"
        else -> "Updates and alerts for your account"
    }
    if (subtitle == subtitle) {
        Log.d("Notifications", "subtitle=" + subtitle)
    }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Notifications",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    // Only offered while something is actually unread.
                    if (hasUnread) {
                        TextButton(
                            onClick = { viewModel.markAllRead(onMarkedAllRead) },
                            enabled = !uiState.isMarkingAllRead,
                        ) {
                            Text(if (uiState.isMarkingAllRead) "Marking…" else "Mark All As Read")
                        }
                    }
                }
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

                hasNotifications -> {
                    items(
                        items = uiState.notifications,
                        key = { it.id },
                    ) { notification ->
                        NotificationRow(
                            notification = notification,
                            isActing = uiState.actingToken == notification.actionToken,
                            isDeleting = uiState.deletingId == notification.id,
                            onAccept = {
                                notification.actionToken?.let { token ->
                                    viewModel.accept(token, onAccepted)
                                }
                            },
                            onDecline = {
                                notification.actionToken?.let(viewModel::decline)
                            },
                            onDelete = { viewModel.delete(notification.id) },
                        )
                        HorizontalDivider()
                    }
                }

                else -> {
                    item {
                        CanMakanMascotEmptyState(
                            title = "No notifications yet",
                            body = "Invites and other account updates will show up here.",
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
private fun NotificationRow(
    notification: UserNotificationResponse,
    isActing: Boolean,
    isDeleting: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                notification.body?.takeIf { it.isNotBlank() }?.let { body ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = body,
                        color = TextSecondary,
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                enabled = !isDeleting && !isActing,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete notification",
                )
            }
        }
        if (notification.expired && notification.canAcceptOrDecline) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "This invitation has expired",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }

        if (notification.canAcceptOrDecline) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onAccept,
                    enabled = !notification.expired && !isActing && !isDeleting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isActing) "Working…" else "Accept")
                }
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !isActing && !isDeleting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Decline")
                }
            }
        }
    }
}
