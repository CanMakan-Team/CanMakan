package sg.edu.nus.iss.canmakan.features.family.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeRestrictionSum
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar

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
    onMenuClick: () -> Unit,
    onNavigateToEditMembers: () -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                AppTopBar(onMenuClick = onMenuClick)
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
                        members = state.data.familyMembers
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatrixGrid(members: List<FamilyMeRestrictionSum>) {
    val activeMembers = members.filter { it.isActive }

    if (activeMembers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No Active Family Members to Display.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }
    val allRestrictions = activeMembers
        .flatMap { it.restrictions }
        .distinctBy { it.code }

    val groupedRestrictions = allRestrictions.groupBy { getCategoryForCode(it.code) }
    val categoryOrder = listOf("Religious", "Allergies & Intolerances", "Specific Diets")

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Family Allergies & Dietary Summary",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                textAlign = TextAlign.Center
            )
        }

        stickyHeader {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(modifier = Modifier.weight(2f))

                activeMembers.forEach { member ->
                    val firstName = member.name.substringBefore(" ")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = firstName,
                            modifier = Modifier.rotate(-90f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                            val hasRestriction = member.restrictions.any { it.code == restriction.code }

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hasRestriction) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Not Selected",
                                        tint = Color(0xFFF44336),
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
}

@Composable
private fun EmptyStateView(onNavigateToEditMembers: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No Family Members or Restrictions Found.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToEditMembers) {
            Text("Manage or Edit Family Members")
        }
    }
}

private fun getCategoryForCode(code: String): String {
    return when (code.uppercase()) {
        "HALAL", "HINDU" -> "Religious"
        "GLUTEN", "DAIRY", "PEANUT", "SHELLFISH", "FISH", "SOY", "EGG" -> "Allergies & Intolerances"
        "VEGETARIAN", "VEGAN", "LOW_SUGAR", "LOW_FAT", "LOW_TRANS_FAT", "LOW_SODIUM" -> "Specific Diets"
        else -> "Other"
    }
}