package sg.edu.nus.iss.canmakan.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.CardWhite
import sg.edu.nus.iss.canmakan.shared.ui.theme.OnDark
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextPrimary

// Top row with the menu button, the app name, and the notification bell.
// This is reused at the top of both the Scanner and History screens.
@Composable
fun AppTopBar(
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.Menu, contentDescription = "Open menu")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("CanMakan", fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Box {
            IconButton(onClick = onNotificationsClick) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(AvoidRed)
            )
        }
    }
}

// Small pill showing the currently active profile, for example "ME Sarah".
@Composable
fun ActiveProfileChip(profile: DietaryProfile) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardWhite)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(PrimaryGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = profile.initials,
                color = OnDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(profile.profileName, fontWeight = FontWeight.Medium, color = TextPrimary)
        Spacer(modifier = Modifier.width(4.dp))
        Text("\u203A", color = TextPrimary)
    }
}
