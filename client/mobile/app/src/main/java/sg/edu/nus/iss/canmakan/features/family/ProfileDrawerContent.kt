package sg.edu.nus.iss.canmakan.features.family

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.DrawerBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.DrawerTextMuted
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen

// Content shown inside the side drawer: the active profile, the list of
// profiles to switch between, quick navigation links, and sign out.
@Composable
fun ProfileDrawerContent(
    profiles: List<DietaryProfile>,
    activeProfile: DietaryProfile,
    onProfileSelected: (DietaryProfile) -> Unit,
    onEditDietaryClick: () -> Unit,
    onScannerClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(DrawerBackground)
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("CanMakan", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Smart Dietary Assistant", color = DrawerTextMuted)
            }
            IconButton(onClick = onCloseClick) {
                Icon(Icons.Default.Close, contentDescription = "Close menu", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("ACTIVE PROFILE", color = DrawerTextMuted)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(initials = activeProfile.initials, background = PrimaryGreen)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(activeProfile.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    AdminTag()
                }
                Text(activeProfile.role, color = DrawerTextMuted)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onEditDietaryClick, modifier = Modifier.fillMaxWidth()) {
            Text("Edit dietary requirements", color = DrawerTextMuted)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("SWITCH PROFILE", color = DrawerTextMuted)
        Spacer(modifier = Modifier.height(8.dp))

        profiles.forEach { profile ->
            val isActive = profile.name == activeProfile.name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) PrimaryGreen.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onProfileSelected(profile) }
                    .padding(10.dp)
            ) {
                InitialsAvatar(initials = profile.initials, background = avatarColorFor(profile.name))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, color = Color.White, fontWeight = FontWeight.Medium)
                    Text(profile.role, color = DrawerTextMuted)
                }
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PrimaryGreen)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("NAVIGATE", color = DrawerTextMuted)
        Spacer(modifier = Modifier.height(8.dp))

        DrawerNavRow(icon = Icons.Default.CropFree, label = "Scanner", onClick = onScannerClick)
        Spacer(modifier = Modifier.height(4.dp))
        DrawerNavRow(
            icon = Icons.Default.AccessTime,
            label = "History",
            isSelected = true,
            onClick = onHistoryClick
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { onSignOutClick() }
                .padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = "Sign out", tint = AvoidRed)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", color = AvoidRed)
        }
    }
}

@Composable
private fun InitialsAvatar(initials: String, background: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdminTag() {
    Text(
        text = "Admin",
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PrimaryGreen)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun DrawerNavRow(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) PrimaryGreen else Color.Transparent)
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = Color.White)
    }
}

// Gives each non-active profile a distinct avatar color, matching the
// original design: orange for Alice, blue for Ben, purple for Grandma.
private fun avatarColorFor(name: String): Color = when (name) {
    "Alice" -> Color(0xFFD9752B)
    "Ben" -> Color(0xFF2B6FD9)
    "Grandma" -> Color(0xFF8B4FD9)
    else -> PrimaryGreen
}
