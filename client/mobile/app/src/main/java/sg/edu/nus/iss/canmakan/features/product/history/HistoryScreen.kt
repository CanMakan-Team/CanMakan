package sg.edu.nus.iss.canmakan.features.product.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sg.edu.nus.iss.canmakan.features.product.model.ScanHistoryEntry
import sg.edu.nus.iss.canmakan.features.product.model.ScanStatus
import sg.edu.nus.iss.canmakan.shared.model.UserProfile
import sg.edu.nus.iss.canmakan.shared.ui.ActiveProfileChip
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.StatusBadge
import sg.edu.nus.iss.canmakan.shared.ui.statusAccentColor
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary
import sg.edu.nus.iss.canmakan.shared.ui.theme.WarningAmber

// Shows the list of previously scanned products for the active profile.
@Composable
fun HistoryScreen(
    activeProfile: UserProfile,
    entries: List<ScanHistoryEntry>,
    onMenuClick: () -> Unit,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onEntryClick: (ScanHistoryEntry) -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                AppTopBar(onMenuClick = onMenuClick)
                Spacer(modifier = Modifier.height(8.dp))
                ActiveProfileChip(profile = activeProfile)
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
        Column(modifier = Modifier.padding(innerPadding)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Scan History",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text("Recent scans for ${activeProfile.name}", color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
            }
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
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

@Composable
private fun ScanHistoryRow(entry: ScanHistoryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(56.dp)
                .background(statusAccentColor(entry.status))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(entry.product.name, fontWeight = FontWeight.Medium)
            Text("${entry.product.brand} \u00B7 ${entry.date}", color = TextSecondary)
            entry.note?.let { note ->
                val noteColor = if (entry.status == ScanStatus.AVOID) AvoidRed else WarningAmber
                Text(note, color = noteColor)
            }
        }
        Box(modifier = Modifier.padding(end = 12.dp)) {
            StatusBadge(status = entry.status)
        }
    }
}
