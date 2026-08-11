package sg.edu.nus.iss.canmakan.features.family.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sg.edu.nus.iss.canmakan.features.family.data.FamilyMeRestrictionSum
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.shared.ui.StatusBadge

/**
 * (UC6) Handles the UI Component and Renderings.
 * This implementation uses TopAppBar for navigation and builds a scrollable matrix.
 * StatusBadge is used to represent the severity of overlapping restrictions.
 */

@Composable
fun FamilyRestrictionSummaryScreen(
    viewModel: FamilyRestrictionSummaryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditMembers: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchSummary()
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Family Allergies", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                        columns = state.uniqueRestrictions
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixGrid(members: List<FamilyMeRestrictionSum>, columns: List<String>) {
    val horizontalScrollState = rememberScrollState()

    Box(
        modifier = Modifier
        .fillMaxSize()
        .horizontalScroll(horizontalScrollState)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight()
        ) {
            // 1. Header Row
            Row(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                Text(
                    text = "Family Member",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(120.dp)
                )
                columns.forEach { restriction ->
                    Text(
                        text = restriction,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }
            HorizontalDivider()

            // 2. Data Rows
            LazyColumn {
                items(members) { member ->
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = member.name,
                            modifier = Modifier.width(120.dp)
                        )
                        columns.forEach { columnName ->
                            val restriction =
                                member.restrictions.find { it.displayName == columnName }
                            Box(
                                modifier = Modifier.width(100.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (restriction != null) {
                                    // Map the string severity to ScanVerdict
                                    val verdict = when (restriction.severity.uppercase()) {
                                        "STRICT_AVOID", "STRICT", "HIGH", "UNSAFE" -> ScanVerdict.UNSAFE
                                        "INTOLERANCE", "WARNING", "MEDIUM", "MODERATE" -> ScanVerdict.WARNING
                                        else -> ScanVerdict.SAFE
                                    }
                                    StatusBadge(status = verdict)
                                } else {
                                    Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
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