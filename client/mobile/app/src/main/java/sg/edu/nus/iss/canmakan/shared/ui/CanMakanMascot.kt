package sg.edu.nus.iss.canmakan.shared.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sg.edu.nus.iss.canmakan.R
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextPrimary
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

/**
 * Pose variants of the CanMakan mascot. Prefer semantic poses over reusing one image:
 * Wave for greetings/empty, Scan for scanner, Safe/Warning/Unsafe for verdicts.
 */
enum class CanMakanMascotPose(@DrawableRes val drawableRes: Int) {
    Wave(R.drawable.canmakan_mascot_wave),
    Scan(R.drawable.canmakan_mascot_scan),
    Safe(R.drawable.canmakan_mascot_safe),
    Warning(R.drawable.canmakan_mascot_warning),
    Unsafe(R.drawable.canmakan_mascot_unsafe),
}

enum class CanMakanMascotSize(val dp: Dp) {
    Compact(48.dp),
    Banner(72.dp),
    Medium(96.dp),
    Large(140.dp),
    Hero(168.dp),
}

@Composable
fun CanMakanMascot(
    modifier: Modifier = Modifier,
    pose: CanMakanMascotPose = CanMakanMascotPose.Wave,
    size: CanMakanMascotSize = CanMakanMascotSize.Medium,
    contentDescription: String? = "CanMakan mascot",
) {
    Image(
        painter = painterResource(id = pose.drawableRes),
        contentDescription = contentDescription,
        modifier = modifier.size(size.dp),
        contentScale = ContentScale.Fit,
    )
}

/**
 * Centered empty-state block with mascot, title, optional body, and optional action slot.
 */
@Composable
fun CanMakanMascotEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    pose: CanMakanMascotPose = CanMakanMascotPose.Wave,
    mascotSize: CanMakanMascotSize = CanMakanMascotSize.Large,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CanMakanMascot(pose = pose, size = mascotSize)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        if (!body.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(16.dp))
            action()
        }
    }
}
