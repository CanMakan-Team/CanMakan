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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    selfProfileId: Long? = null,
    memberRole: String? = null,
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
    onManageFamilyClick: () -> Unit,
) {
    // Session-local expand/collapse; all sections start open. Not persisted across process death.
    var profilesExpanded by remember { mutableStateOf(true) }
    var navigateExpanded by remember { mutableStateOf(true) }
    var familyExpanded by remember { mutableStateOf(true) }

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
        DrawerSectionHeader(
            title = if (profiles.size > 1) "PROFILES" else "PROFILE",
            expanded = profilesExpanded,
            onToggle = { profilesExpanded = !profilesExpanded },
            trailing = {
                if (isSwitchingProfile) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryGreen,
                    )
                }
            },
        )

        if (profilesExpanded) {
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
                                val tags = ProfileRelationshipDisplay.tags(
                                    profileId = profile.id,
                                    relationship = profile.relationship,
                                    isFamilyAdminProfile = profile.isPrimary,
                                    viewerSelfProfileId = selfProfileId,
                                    viewerMemberRole = memberRole,
                                )
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
                                    if (tags.showAdminTag) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        AdminTag()
                                    }
                                }
                                tags.caption?.let { caption ->
                                    Text(
                                        text = caption,
                                        color = DrawerTextMuted,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
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
        }

        Spacer(modifier = Modifier.height(10.dp))

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 1.dp,
            color = Divider
        )

        Spacer(modifier = Modifier.height(10.dp))
        DrawerSectionHeader(
            title = "NAVIGATE",
            expanded = navigateExpanded,
            onToggle = { navigateExpanded = !navigateExpanded },
        )

        if (navigateExpanded) {
            Spacer(modifier = Modifier.height(8.dp))

            val isScannerSelected = currentRoute == "scanner"
            NavigationDrawerItem(
                label = {
                    Text(
                        text = "Scanner",
                        color = if (isScannerSelected) Divider else OnDark,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Normal
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = "Scanner",
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
        }

        // Family section: dietary summary for all members; manage actions for PRIMARY_ADMIN.
        if (hasFamily) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color = Divider
            )
            Spacer(modifier = Modifier.height(10.dp))
            DrawerSectionHeader(
                title = "FAMILY",
                expanded = familyExpanded,
                onToggle = { familyExpanded = !familyExpanded },
            )

            if (familyExpanded) {
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
                    val isManageSelected = currentRoute?.startsWith("family/manage") == true ||
                        currentRoute?.startsWith("family/invite") == true ||
                        currentRoute?.startsWith("family/dependant") == true
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = "Manage Family",
                                color = if (isManageSelected) Divider else OnDark,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Normal,
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Manage Family",
                                tint = if (isManageSelected) Divider else OnDark,
                            )
                        },
                        selected = isManageSelected,
                        onClick = onManageFamilyClick,
                    )
                }
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
private fun DrawerSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = title,
            color = DrawerTextMuted,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            tint = DrawerTextMuted,
            modifier = Modifier.size(20.dp),
        )
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

