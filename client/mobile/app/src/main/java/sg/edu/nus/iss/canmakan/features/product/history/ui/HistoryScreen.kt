package sg.edu.nus.iss.canmakan.features.product.history.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sg.edu.nus.iss.canmakan.features.product.model.ScanHistoryEntry
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.ActiveProfileChip
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotEmptyState
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotPose
import sg.edu.nus.iss.canmakan.shared.ui.StatusBadge
import sg.edu.nus.iss.canmakan.shared.ui.statusAccentColor
import sg.edu.nus.iss.canmakan.shared.ui.theme.CardWhite
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary
import sg.edu.nus.iss.canmakan.shared.util.toScanHistoryDisplayString

// Shows the list of previously scanned products for the active profile.
@Composable
fun HistoryScreen(
    activeProfile: DietaryProfile?,
    entries: List<ScanHistoryEntry>,
    isLoading: Boolean = false,
    requiresProfileSetup: Boolean = false,
    errorMessage: String? = null,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSetUpProfile: () -> Unit,
    onEntryClick: (ScanHistoryEntry) -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                AppTopBar(
                    onMenuClick = onMenuClick,
                    onNotificationsClick = onNotificationsClick,
                )
                Spacer(modifier = Modifier.height(8.dp))
                activeProfile?.let { ActiveProfileChip(profile = it) }
            }
        },
        bottomBar = {
            AppBottomNavBar(
                selectedTab = BottomTab.HISTORY,
                onScanClick = onScanClick,
                onHistoryClick = onHistoryClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Scan History",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    if (activeProfile == null) {
                        "Personalised history becomes available after profile setup."
                    } else {
                        "Recent scans for ${activeProfile.profileName}"
                    },
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            when {
                requiresProfileSetup || activeProfile == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CanMakanMascotEmptyState(
                        title = "Set up your dietary profile",
                        body = "Personalised scan history becomes available after profile setup.",
                        pose = CanMakanMascotPose.Wave,
                        action = {
                            Button(onClick = onSetUpProfile) {
                                Text("Set up profile")
                            }
                        },
                    )
                }
                isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                errorMessage != null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }
                entries.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CanMakanMascotEmptyState(
                        title = "No scans yet",
                        body = "Scan a barcode to check if ${activeProfile.profileName} can makan.",
                        pose = CanMakanMascotPose.Scan,
                        action = {
                            Button(onClick = onScanClick) {
                                Text("Go to Scanner")
                            }
                        },
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries) { entry ->
                        ScanHistoryRow(entry = entry, onClick = { onEntryClick(entry) })
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }
}

private val ScanHistoryRowHeight = 104.dp

@Composable
private fun ScanHistoryRow(entry: ScanHistoryEntry, onClick: () -> Unit) {
    val brand = entry.product.displayBrand
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ScanHistoryRowHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(CardWhite)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(statusAccentColor(entry.verdict))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = entry.product.displayName,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Always reserve one brand line so rows stay the same height when brand is missing.
            Text(
                text = brand.ifBlank { " " },
                color = if (brand.isBlank()) Color.Transparent else TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.scannedAt.toScanHistoryDisplayString(),
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.padding(end = 12.dp)) {
            StatusBadge(status = entry.verdict)
        }
    }
}
