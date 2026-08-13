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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
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
import java.util.Locale
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.RestrictionEditAuthorization
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvatarBlue
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvatarOrange
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvatarPurple
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.Divider
import sg.edu.nus.iss.canmakan.shared.ui.theme.DrawerBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.DrawerTextMuted
import sg.edu.nus.iss.canmakan.shared.ui.theme.OnDark
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen

// Content shown inside the side drawer: selectable profiles (selected row
// expands with dietary actions), quick navigation links, and sign out.
@Composable
fun ProfileDrawerContent(
    currentRoute: String?,
    profiles: List<DietaryProfile>,
    activeProfile: DietaryProfile?,
    hasFamily: Boolean,
    hasUserSession: Boolean,
    noFamilyMessage: String?,
    showManageFamilyActions: Boolean,
    isSwitchingProfile: Boolean = false,
    onProfileSelected: (DietaryProfile) -> Unit,
    onEditDietaryClick: () -> Unit,
    editDietaryButtonLabel: String = RestrictionEditAuthorization.EDIT_DIETARY_PROFILE_LABEL,
    onScannerClick: () -> Unit,
    onFamilyAllergySummaryClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onCloseClick: () -> Unit,
    onCreateFamilyCircleClick: () -> Unit,
    onCreateNewClick: () -> Unit,
    onAddProfileClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(DrawerBackground)
    ) {
        // Scrollable region: everything except the pinned sign-out/settings footer.
        // A plain Column with weight(1f) here (rather than wrapping the whole drawer
        // in verticalScroll) keeps the footer fixed at the bottom regardless of how
        // much content is above it.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text("CanMakan", color = OnDark, fontWeight = FontWeight.Bold)
                Text("Smart Dietary Assistant", color = DrawerTextMuted)
            }
            IconButton(onClick = onCloseClick) {
                Icon(Icons.Default.Close, contentDescription = "Close menu", tint = OnDark)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (profiles.size > 1) "PROFILES" else "PROFILE",
                color = DrawerTextMuted,
                style = MaterialTheme.typography.titleSmall,
            )
            if (isSwitchingProfile) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = PrimaryGreen,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (profiles.isEmpty()) {
            Text(
                text = "No profile selected",
                color = DrawerTextMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(10.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onEditDietaryClick, modifier = Modifier.fillMaxWidth()) {
                Text(editDietaryButtonLabel, color = DrawerTextMuted)
            }
        } else {
            profiles.forEach { profile ->
                val isActive = profile.id == activeProfile?.id
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) PrimaryGreen.copy(alpha = 0.25f) else Color.Transparent)
                        .padding(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProfileSelected(profile) },
                    ) {
                        InitialsAvatar(
                            initials = profile.initials,
                            background = if (isActive) PrimaryGreen else avatarColorFor(profile),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile.profileName,
                                    color = OnDark,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    style = if (isActive) {
                                        MaterialTheme.typography.titleMedium
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    },
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (profile.isPrimary) AdminTag()
                            }
                            Text(
                                text = formatRelationshipLabel(profile.relationship),
                                color = DrawerTextMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryGreen),
                            )
                        }
                    }
                    if (isActive) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onEditDietaryClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(editDietaryButtonLabel, color = DrawerTextMuted)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        if (!hasFamily) {
            Spacer(modifier = Modifier.height(12.dp))
            if (!noFamilyMessage.isNullOrBlank()) {
                Text(
                    text = noFamilyMessage,
                    color = DrawerTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (hasUserSession) {
                OutlinedButton(
                    onClick = onCreateFamilyCircleClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create family circle", color = DrawerTextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 1.dp,
            color = Divider
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text("NAVIGATE", color = DrawerTextMuted, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        val isScannerSelected = currentRoute == "scanner"
        NavigationDrawerItem(
            label = {
                Text(
                    text = "Scanner",
                    // Switch to a dark color when selected, otherwise remain White
                    color = if (isScannerSelected) Divider else OnDark,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Normal
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = "Scanner",
                    // You can also apply the same conditional logic to the Icon tint if desired
                    tint = if (isScannerSelected) Divider else OnDark
                )
            },
            selected = isScannerSelected,
            onClick = onScannerClick
        )

        val isHistorySelected = currentRoute == "history"
        NavigationDrawerItem(
            label = {
                Text(
                    text = "History",
                    color = if (isHistorySelected) Divider else OnDark,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Normal
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = "History",
                    tint = if (isHistorySelected) Divider else OnDark
                )
            },
            selected = isHistorySelected,
            onClick = onHistoryClick
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Family section: dietary summary for all members; manage actions for PRIMARY_ADMIN.
        if (hasFamily) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color = Divider
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("FAMILY", color = DrawerTextMuted, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            val isFamilySelected = currentRoute?.startsWith("family/restrictions") == true
            NavigationDrawerItem(
                label = {
                    Text(
                        text = "Dietary Summary",
                        color = if (isFamilySelected) Divider else OnDark,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Normal
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = "Dietary Summary",
                        tint = if (isFamilySelected) Divider else OnDark
                    )
                },
                selected = isFamilySelected,
                onClick = onFamilyAllergySummaryClick
            )

            if (showManageFamilyActions) {
                Spacer(modifier = Modifier.height(4.dp))
                DrawerNavRow(
                    icon = Icons.Default.PersonAdd,
                    label = "Create New Family Member",
                    onClick = onCreateNewClick,
                )
                Spacer(modifier = Modifier.height(4.dp))
                DrawerNavRow(
                    icon = Icons.Default.Group,
                    label = "Add Profile to Family",
                    onClick = onAddProfileClick,
                )
            }
        }
        }

        // Pinned footer, kept outside the scrollable Column above so it stays
        // visible at the bottom of the drawer regardless of scroll position.
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {

            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onSignOutClick() }
                    .padding(vertical = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out", tint = AvoidRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out", color = AvoidRed)
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "", tint = OnDark)

            }
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
        Text(initials, color = OnDark, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdminTag() {
    Text(
        text = "Admin",
        color = OnDark,
        style = MaterialTheme.typography.labelSmall,
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
        Icon(icon, contentDescription = label, tint = OnDark)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = OnDark, style = MaterialTheme.typography.bodyMedium)
    }
}

// Derive a stable avatar color from the profile's identity instead of
// hardcoding specific names.
private fun avatarColorFor(profile: DietaryProfile): Color {
    val hash = profile.profileName.trim().lowercase().hashCode()
    return when (hash % 4) {
        0 -> AvatarOrange
        1 -> AvatarBlue
        2 -> AvatarPurple
        else -> PrimaryGreen
    }
}

/** Display label for relationship codes */
private fun formatRelationshipLabel(relationship: String): String {
    val trimmed = relationship.trim()
    if (trimmed.equals("DEPENDENT", ignoreCase = true)
        || trimmed.equals("DEPENDANT", ignoreCase = true)
    ) {
        return "Dependant"
    }
    if (trimmed.isEmpty()) return trimmed
    return trimmed.lowercase(Locale.getDefault())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
