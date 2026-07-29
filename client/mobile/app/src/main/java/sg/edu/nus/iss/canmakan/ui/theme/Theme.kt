package sg.edu.nus.iss.canmakan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PrimaryGreen,
    background = BackgroundCream,
    surface = CardWhite,
    error = AvoidRed
)

@Composable
fun CanMakanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = CanMakanTypography,
        content = content
    )
}
