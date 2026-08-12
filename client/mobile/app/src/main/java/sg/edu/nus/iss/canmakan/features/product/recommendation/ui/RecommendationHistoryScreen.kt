package sg.edu.nus.iss.canmakan.features.product.recommendation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
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
import sg.edu.nus.iss.canmakan.features.product.recommendation.model.RecommendationHistoryEntry
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.ActiveProfileChip
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.StatusBadge
import sg.edu.nus.iss.canmakan.shared.ui.statusAccentColor
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

@Composable
fun RecommendationHistoryScreen(
    activeProfile: DietaryProfile?,
    entries: List<RecommendationHistoryEntry>,
    isLoading: Boolean = false,
    requiresProfileSetup: Boolean = false,
    errorMessage: String? = null,
    onMenuClick: () -> Unit,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSetUpProfile: () -> Unit,
    onEntryClick: (RecommendationHistoryEntry) -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                AppTopBar(onMenuClick = onMenuClick)
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
        Column(modifier = Modifier.padding(innerPadding)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Recommendation History",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    if (activeProfile == null) {
                        "Personalised recommendations become available after profile setup."
                    } else {
                        "Past alternatives shown for ${activeProfile.profileName}"
                    },
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            when {
                requiresProfileSetup || activeProfile == null -> Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Set up your dietary profile to view personalised recommendations.")
                        Button(onClick = onSetUpProfile) {
                            Text("Set up profile")
                        }
                    }
                }
                isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                errorMessage != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }
                entries.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No past recommendations yet. Scan a product that needs alternatives to build history.",
                        color = TextSecondary
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries) { entry ->
                        RecommendationHistoryRow(
                            entry = entry,
                            onClick = { onEntryClick(entry) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RecommendationHistoryRow(
    entry: RecommendationHistoryEntry,
    onClick: () -> Unit
) {
    val verdict = entry.verdict()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(statusAccentColor(verdict))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(entry.sourceProductName, fontWeight = FontWeight.Medium)
            if (entry.sourceBrand.isNotBlank()) {
                Text(entry.sourceBrand, color = TextSecondary)
            }
            val alternativeCount = entry.alternatives.size
            Text(
                text = if (alternativeCount == 1) {
                    "1 alternative suggested"
                } else {
                    "$alternativeCount alternatives suggested"
                },
                color = TextSecondary
            )
            entry.recommendedAtDisplay()
                .takeIf { it.isNotBlank() }
                ?.let { timestamp ->
                    Text(timestamp, color = TextSecondary)
                }
        }
        Box(modifier = Modifier.padding(end = 12.dp)) {
            StatusBadge(status = verdict)
        }
    }
}
