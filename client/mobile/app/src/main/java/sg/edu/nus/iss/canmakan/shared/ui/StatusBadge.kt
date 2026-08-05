package sg.edu.nus.iss.canmakan.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightAmberBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightRedBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.WarningAmber

// Small colored pill used to show whether a product is safe, needs
// caution, or should be avoided.
@Composable
fun StatusBadge(status: ScanVerdict) {
    val background = when (status) {
        ScanVerdict.SAFE -> LightGreenBackground
        ScanVerdict.WARNING -> LightAmberBackground
        ScanVerdict.UNSAFE -> LightRedBackground
    }
    val textColor = statusAccentColor(status)
    val label = when (status) {
        ScanVerdict.SAFE -> "SAFE"
        ScanVerdict.WARNING -> "WARNING"
        ScanVerdict.UNSAFE -> "AVOID"
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
fun statusAccentColor(status: ScanVerdict) = when (status) {
    ScanVerdict.SAFE -> PrimaryGreen
    ScanVerdict.WARNING -> WarningAmber
    ScanVerdict.UNSAFE -> AvoidRed
}
