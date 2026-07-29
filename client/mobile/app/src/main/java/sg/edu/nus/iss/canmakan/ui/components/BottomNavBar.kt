package sg.edu.nus.iss.canmakan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import sg.edu.nus.iss.canmakan.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.ui.theme.TextSecondary

// The two possible tabs in the bottom navigation bar.
enum class BottomTab { SCAN, HISTORY }

@Composable
fun AppBottomNavBar(
    selectedTab: BottomTab,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomNavItem(
            label = "Scan",
            icon = Icons.Default.CropFree,
            isSelected = selectedTab == BottomTab.SCAN,
            onClick = onScanClick
        )
        BottomNavItem(
            label = "History",
            icon = Icons.Default.AccessTime,
            isSelected = selectedTab == BottomTab.HISTORY,
            onClick = onHistoryClick
        )
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (isSelected) PrimaryGreen else TextSecondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, color = tint)
    }
}
