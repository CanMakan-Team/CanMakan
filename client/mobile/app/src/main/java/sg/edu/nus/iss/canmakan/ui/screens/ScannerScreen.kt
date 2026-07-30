package sg.edu.nus.iss.canmakan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import sg.edu.nus.iss.canmakan.data.DietaryProfile
import sg.edu.nus.iss.canmakan.ui.components.ActiveProfileChip
import sg.edu.nus.iss.canmakan.ui.components.AppBottomNavBar
import sg.edu.nus.iss.canmakan.ui.components.AppTopBar
import sg.edu.nus.iss.canmakan.ui.components.BottomTab
import sg.edu.nus.iss.canmakan.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.ui.theme.TextSecondary

// The main scanner screen. Shows a placeholder camera preview, the
// "Tap to Scan" action, and the currently active restrictions.
@Composable
fun ScannerScreen(
    activeProfile: DietaryProfile,
    activeRestrictions: List<String>,
    onMenuClick: () -> Unit,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit
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
                selectedTab = BottomTab.SCAN,
                onScanClick = onScanClick,
                onHistoryClick = onHistoryClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Barcode Scanner",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
            Text("Point camera at a product barcode", color = TextSecondary)

            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF10151C)),
                contentAlignment = Alignment.Center
            ) {
                Text("Align barcode within frame", color = Color(0xFF8FA0AE))
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Tap to Scan", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "ACTIVE RESTRICTIONS \u2014 ${activeProfile.name.uppercase()}",
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row {
                        activeRestrictions.forEach { restriction ->
                            RestrictionPill(text = restriction)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestrictionPill(text: String) {
    Text(
        text = text,
        color = PrimaryGreen,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFDCF0E6))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
