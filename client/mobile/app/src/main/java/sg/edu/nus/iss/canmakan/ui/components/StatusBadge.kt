package sg.edu.nus.iss.canmakan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import sg.edu.nus.iss.canmakan.data.ScanStatus
import sg.edu.nus.iss.canmakan.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.ui.theme.LightAmberBackground
import sg.edu.nus.iss.canmakan.ui.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.ui.theme.LightRedBackground
import sg.edu.nus.iss.canmakan.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.ui.theme.WarningAmber

// Small colored pill used to show whether a product is safe, needs
// caution, or should be avoided.
@Composable
fun StatusBadge(status: ScanStatus) {
    val background = when (status) {
        ScanStatus.SAFE -> LightGreenBackground
        ScanStatus.WARNING -> LightAmberBackground
        ScanStatus.AVOID -> LightRedBackground
    }
    val textColor = statusAccentColor(status)
    val label = when (status) {
        ScanStatus.SAFE -> "SAFE"
        ScanStatus.WARNING -> "WARNING"
        ScanStatus.AVOID -> "AVOID"
    }

    Text(
        text = label,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// Returns the accent color used for a status, both for the badge text
// and for the colored stripe on the left edge of a history row.
fun statusAccentColor(status: ScanStatus) = when (status) {
    ScanStatus.SAFE -> PrimaryGreen
    ScanStatus.WARNING -> WarningAmber
    ScanStatus.AVOID -> AvoidRed
}
