package sg.edu.nus.iss.canmakan.features.family.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import sg.edu.nus.iss.canmakan.features.family.ProfileRelationshipDisplay
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeRestrictionDetail
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeRestrictionSum
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotEmptyState
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotPose
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvatarBlue
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvatarOrange
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvatarPurple
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.OnDark
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

/**
 * (UC6) Handles the UI Component and Renderings.
 * V1: This implementation uses TopAppBar for navigation and builds a scrollable matrix.
 * StatusBadge is used to represent the severity of overlapping restrictions.
 *
 * V2: This implementation uses AppTopBar for navigation and builds a scrollable, inverted matrix
 * with sticky headers, categorized rows, and boolean indicators.
 */

@Composable
fun FamilyRestrictionSummaryScreen(
    uiState: FamilyRestrictionSummaryUiState,
    profiles: List<DietaryProfile> = emptyList(),
    selfProfileId: Long? = null,
    memberRole: String? = null,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    hasUnreadNotifications: Boolean = false,
    onNavigateToEditMembers: () -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                AppTopBar(
                    onMenuClick = onMenuClick,
                    onNotificationsClick = onNotificationsClick,
                    hasUnreadNotifications = hasUnreadNotifications,
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is FamilyRestrictionSummaryUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is FamilyRestrictionSummaryUiState.Empty -> {
                    EmptyStateView(onNavigateToEditMembers)
                }
                is FamilyRestrictionSummaryUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is FamilyRestrictionSummaryUiState.Success -> {
                    MatrixGrid(
                        members = state.data.familyMembers,
                        profiles = profiles,
                        selfProfileId = selfProfileId,
                        memberRole = memberRole,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatrixGrid(
    members: List<FamilyMeRestrictionSum>,
    profiles: List<DietaryProfile>,
    selfProfileId: Long?,
    memberRole: String?,
) {
    val activeMembers = members.filter { it.isActive }
    var peekedMember by remember { mutableStateOf<FamilyMeRestrictionSum?>(null) }

    if (activeMembers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No Active Family Members to Display.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }
    val allRestrictions = consolidateRestrictionsForMatrix(
        activeMembers.flatMap { it.restrictions },
    )

    val groupedRestrictions = allRestrictions.groupBy { it.category }
    val categoryOrder = listOf("Religious", "Allergies & Intolerances", "Specific Diets", "Other")
    val profilesById = remember(profiles) { profiles.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Dietary Summary",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap a profile for details",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        stickyHeader {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(2f))

                activeMembers.forEach { member ->
                    val profile = member.profileId?.let { profilesById[it] }
                    val initials = profile?.initials?.takeIf { it.isNotBlank() }
                        ?: initialsFromName(member.name)
                    val avatarColor = avatarColorFor(member)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(avatarColor)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "View profile for ${member.name}"
                                }
                                .clickable { peekedMember = member },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = initials,
                                color = OnDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        categoryOrder.forEach { categoryName ->
            val restrictionsInCategory = groupedRestrictions[categoryName]

            if (!restrictionsInCategory.isNullOrEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = categoryName,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }

                items(restrictionsInCategory) { restriction ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = restriction.displayName,
                            modifier = Modifier
                                .weight(2f)
                                .padding(start = 16.dp, end = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        activeMembers.forEach { member ->
                            val hasRestriction = member.restrictions.any { detail ->
                                detail.code.uppercase() in restriction.matchCodes
                            }

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hasRestriction) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = PrimaryGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Not Selected",
                                        tint = AvoidRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }

    peekedMember?.let { member ->
        val profile = member.profileId?.let { profilesById[it] }
        val tags = profile?.let {
            ProfileRelationshipDisplay.tags(
                profileId = it.id,
                relationship = it.relationship,
                isFamilyAdminProfile = it.isPrimary,
                viewerSelfProfileId = selfProfileId,
                viewerMemberRole = memberRole,
            )
        }
        ProfileInfoDialog(
            name = member.name,
            caption = ProfileRelationshipDisplay.sheetRoleLine(
                tags ?: ProfileRelationshipDisplay.Tags(showAdminTag = false, caption = null),
            ).ifBlank { null },
            initials = profile?.initials?.takeIf { it.isNotBlank() } ?: initialsFromName(member.name),
            avatarColor = avatarColorFor(member),
            onDismiss = { peekedMember = null },
        )
    }
}

@Composable
private fun ProfileInfoDialog(
    name: String,
    caption: String?,
    initials: String,
    avatarColor: Color,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(bottom = 28.dp),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initials,
                        color = OnDark,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                caption?.takeIf { it.isNotBlank() }?.let { label ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(onNavigateToEditMembers: () -> Unit) {
    CanMakanMascotEmptyState(
        title = "No family members yet",
        body = "Add members to see a shared dietary summary.",
        pose = CanMakanMascotPose.Wave,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        action = {
            Button(onClick = onNavigateToEditMembers) {
                Text("Manage family")
            }
        },
    )
}

/**
 * One matrix row. Dairy-family codes ([DAIRY_FAMILY_CODES]) collapse into a single row so
 * any legacy or alternate spelling of the dairy restriction still displays as one entry;
 * catalog codes stay unchanged. The backend catalog itself now has a single `DAIRY` row.
 */
private data class MatrixRestrictionRow(
    val matchCodes: Set<String>,
    val displayName: String,
    val category: String,
)

/** Codes treated as the same dairy/lactose restriction for display purposes. */
private val DAIRY_FAMILY_CODES = setOf(
    "DAIRY",
    "LACTOSE_INTOLERANT",
    "DAIRY_FREE",
    "LACTOSE",
)

/**
 * Collapse duplicate dairy-family codes into one row; keep other codes as-is.
 * Categories mirror the seeded catalog in 05_household_dietary_data.sql.
 */
private fun consolidateRestrictionsForMatrix(
    details: List<FamilyMeRestrictionDetail>,
): List<MatrixRestrictionRow> {
    val byCode = details
        .map { it.copy(code = it.code.trim().uppercase()) }
        .distinctBy { it.code }

    val dairyDetails = byCode.filter { it.code in DAIRY_FAMILY_CODES }
    val otherDetails = byCode.filterNot { it.code in DAIRY_FAMILY_CODES }

    val rows = mutableListOf<MatrixRestrictionRow>()
    if (dairyDetails.isNotEmpty()) {
        rows += MatrixRestrictionRow(
            matchCodes = DAIRY_FAMILY_CODES,
            displayName = "Lactose Intolerance",
            category = "Allergies & Intolerances",
        )
    }
    rows += otherDetails.map { detail ->
        MatrixRestrictionRow(
            matchCodes = setOf(detail.code),
            displayName = detail.displayName,
            category = categoryForRestrictionCode(detail.code),
        )
    }
    return rows
}

private fun categoryForRestrictionCode(code: String): String {
    return when (code.uppercase()) {
        "HALAL", "KOSHER" -> "Religious"
        "GLUTEN",
        "DAIRY",
        "DAIRY_FREE",
        "LACTOSE",
        "LACTOSE_INTOLERANT",
        "PEANUT",
        "TREE_NUT",
        "SHELLFISH",
        "FISH",
        "SOY",
        "EGG",
        "SESAME",
        -> "Allergies & Intolerances"
        "VEGETARIAN",
        "VEGAN",
        "LOW_SUGAR",
        "LOW_FAT",
        "LOW_TRANS_FAT",
        "LOW_SODIUM",
        "LOW_CHOLESTEROL",
        "KETO",
        -> "Specific Diets"
        else -> "Other"
    }
}

private fun initialsFromName(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts.first().first()}${parts.last().first()}".uppercase()
    }
}

private fun avatarColorFor(member: FamilyMeRestrictionSum): Color {
    val seed = member.profileId ?: member.userId
    return when ((seed % 4).toInt()) {
        0 -> AvatarOrange
        1 -> AvatarBlue
        2 -> AvatarPurple
        else -> PrimaryGreen
    }
}
