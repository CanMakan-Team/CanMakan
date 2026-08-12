package sg.edu.nus.iss.canmakan.features.product.verdict

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.edu.nus.iss.canmakan.features.product.model.AlternativeProduct
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ProductFlag
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.statusAccentColor
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.CardWhite
import sg.edu.nus.iss.canmakan.shared.ui.theme.InfoBlue
import sg.edu.nus.iss.canmakan.shared.ui.theme.InfoBlueContainer
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightRedBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.OnDark
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.SurfaceMuted
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

private enum class DetailTab { FLAGS, ALTERNATIVES }

/* Shows the outcome for one scanned product, with two tabs: the flagged
 * reasons behind the verdict, and safer alternatives to try instead.
 *
 * authors Amelia; Kwok Heng
 */
@Composable
fun ProductDetailScreen(
    product: Product,
    verdict: ScanVerdict, // drives icon/color/label
    flags: List<ProductFlag>,
    alternatives: List<AlternativeProduct>,
    profileName: String,
    explanation: String? = null, // optional explanation for the verdict
    alternativesError: String? = null,
    onBackClick: () -> Unit,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(DetailTab.FLAGS) }
    val showAlternativesTab = verdict != ScanVerdict.SAFE
    val accent = statusAccentColor(verdict)
    val (icon, label) = verdictPresentation(verdict)
    val scrollState = rememberScrollState()

    Scaffold(
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
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .clickable(onClick = onBackClick)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back", color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(65.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = label, tint = OnDark)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(label, color = accent, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    product.productName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (product.brand.isNotBlank()) {
                    Text(
                        product.brand,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    product.barcode,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!explanation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        explanation,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceMuted)
            ) {
                DetailTabButton(
                    label = "Flags & Details",
                    isSelected = selectedTab == DetailTab.FLAGS,
                    modifier = Modifier.weight(1f)
                ) { selectedTab = DetailTab.FLAGS }
                if (showAlternativesTab) {
                    DetailTabButton(
                        label = "Alternatives",
                        isSelected = selectedTab == DetailTab.ALTERNATIVES,
                        modifier = Modifier.weight(1f)
                    ) { selectedTab = DetailTab.ALTERNATIVES }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    DetailTab.FLAGS -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        FlagsAndDetailsTab(flags = flags, profileName = profileName)
                    }
                    DetailTab.ALTERNATIVES -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        AlternativesTab(
                            alternatives = alternatives,
                            profileName = profileName,
                            errorMessage = alternativesError
                        )
                    }
                }
            }
        }
    }
}

// Returns the appropriate icon and label based on the verdict
private fun verdictPresentation(verdict: ScanVerdict): Pair<ImageVector, String> {
    return when (verdict) {
        ScanVerdict.SAFE -> Icons.Default.Check to "SAFE"
        ScanVerdict.WARNING -> Icons.Default.Warning to "WARNING"
        ScanVerdict.UNSAFE -> Icons.Default.Close to "UNSAFE"
    }
}

// Button to switch between tabs in the detail screen
// Tabs: Flags & Details, Alternatives
@Composable
private fun DetailTabButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) CardWhite else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

// Tab 1: Shows the flags and details for the product
@Composable
private fun FlagsAndDetailsTab(flags: List<ProductFlag>, profileName: String) {
    Column {

        // Display a message if there are no flags
        if (flags.isEmpty()) {
            Text(
                "No specific flags for this product.",
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // Display each flag as a separate box
        flags.forEach { flag ->
            val isAllergen = flag.category.equals("ALLERGEN", ignoreCase = true)
            val background = if (isAllergen) LightRedBackground else InfoBlueContainer
            val labelColor = if (isAllergen) AvoidRed else InfoBlue
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .padding(14.dp)
            ) {
                Text(flag.category, color = labelColor)
                Text(flag.label, color = labelColor, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Matched against $profileName's profile",
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

// Tab 2: Shows the alternatives for the product
@Composable
private fun AlternativesTab(
    alternatives: List<AlternativeProduct>,
    profileName: String,
    errorMessage: String? = null
) {
    Column {
        Text("Safe alternatives for $profileName", color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))

        if (!errorMessage.isNullOrBlank()) {
            Text(
                errorMessage,
                color = AvoidRed
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Display a message if there are no alternatives
        if (alternatives.isEmpty()) {
            Text(
                "No alternatives available yet.",
                color = TextSecondary
            )
        }

        // Display each alternative as a separate box
        alternatives.forEach { alternative ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(alternative.name, fontWeight = FontWeight.Bold)
                    Text(alternative.brand, color = TextSecondary)
                    Text(alternative.description, color = TextSecondary)
                }
                Text(
                    "SAFE",
                    color = PrimaryGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(LightGreenBackground)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
