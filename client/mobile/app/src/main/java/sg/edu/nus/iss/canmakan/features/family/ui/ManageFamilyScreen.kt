package sg.edu.nus.iss.canmakan.features.family.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.theme.BorderSubtle
import sg.edu.nus.iss.canmakan.shared.ui.theme.CardWhite
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextPrimary
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

/**
 * PRIMARY_ADMIN hub: choose invite (account joins) or dependant profile (no login).
 * Full roster edit/remove remains web-primary (UC12).
 */
@Composable
fun ManageFamilyScreen(
    familyName: String? = null,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    hasUnreadNotifications: Boolean = false,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onInviteClick: () -> Unit,
    onDependantClick: () -> Unit,
) {
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
                .padding(20.dp),
        ) {
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

            val headingName = familyName?.trim().orEmpty().ifEmpty { "Family" }
            Text(
                text = "Manage $headingName",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add someone to your family circle.",
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            ManageFamilyActionRow(
                icon = Icons.Default.PersonAdd,
                title = "Invite someone",
                subtitle = "They can join with a new or existing account.",
                onClick = onInviteClick,
            )
            Spacer(modifier = Modifier.height(12.dp))
            ManageFamilyActionRow(
                icon = Icons.Default.Person,
                title = "Add a profile",
                subtitle = "For someone you look after, no account needed.",
                onClick = onDependantClick,
            )
        }
    }
}

@Composable
private fun ManageFamilyActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardWhite)
            .border(1.dp, BorderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryGreen)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
        )
    }
}
