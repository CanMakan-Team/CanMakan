package sg.edu.nus.iss.canmakan.productdetail

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.edu.nus.iss.canmakan.data.AlternativeProduct
import sg.edu.nus.iss.canmakan.data.Product
import sg.edu.nus.iss.canmakan.data.ProductFlag
import sg.edu.nus.iss.canmakan.utils.AppBottomNavBar
import sg.edu.nus.iss.canmakan.utils.BottomTab
import sg.edu.nus.iss.canmakan.theme.AvoidRed
import sg.edu.nus.iss.canmakan.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.theme.TextSecondary

private enum class DetailTab { FLAGS, ALTERNATIVES }

// Shows the outcome for one scanned product, with two tabs: the flagged
// reasons behind the verdict, and safer alternatives to try instead.
@Composable
fun ProductDetailScreen(
    product: Product,
    flags: List<ProductFlag>,
    alternatives: List<AlternativeProduct>,
    profileName: String,
    onBackClick: () -> Unit,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(DetailTab.FLAGS) }

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
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .clickable(onClick = onBackClick)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back to history", color = TextSecondary)
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
                        .background(AvoidRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Avoid", tint = Color.White)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("AVOID", color = AvoidRed, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(product.brand, color = TextSecondary)
                Text(product.barcode, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFEDEAE2))
            ) {
                DetailTabButton(
                    label = "Flags & Details",
                    isSelected = selectedTab == DetailTab.FLAGS,
                    modifier = Modifier.weight(1f)
                ) { selectedTab = DetailTab.FLAGS }
                DetailTabButton(
                    label = "Alternatives",
                    isSelected = selectedTab == DetailTab.ALTERNATIVES,
                    modifier = Modifier.weight(1f)
                ) { selectedTab = DetailTab.ALTERNATIVES }
            }

            Spacer(modifier = Modifier.height(16.dp))
            when (selectedTab) {
                DetailTab.FLAGS -> FlagsAndDetailsTab(flags = flags, profileName = profileName)
                DetailTab.ALTERNATIVES -> AlternativesTab(alternatives = alternatives, profileName = profileName)
            }
        }
    }
}

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
            .background(if (isSelected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun FlagsAndDetailsTab(flags: List<ProductFlag>, profileName: String) {
    Column {
        flags.forEach { flag ->
            val isAllergen = flag.category == "ALLERGEN"
            val background = if (isAllergen) Color(0xFFFBE4E3) else Color(0xFFE3EBF7)
            val labelColor = if (isAllergen) AvoidRed else Color(0xFF2B5FA8)
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

@Composable
private fun AlternativesTab(alternatives: List<AlternativeProduct>, profileName: String) {
    Column {
        Text("Safe alternatives for $profileName", color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        alternatives.forEach { alternative ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
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
