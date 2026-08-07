package sg.edu.nus.iss.canmakan.features.family.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.edu.nus.iss.canmakan.features.product.model.ScanHistoryEntry
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.ActiveProfileChip
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.StatusBadge
import sg.edu.nus.iss.canmakan.shared.ui.statusAccentColor
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary
import sg.edu.nus.iss.canmakan.shared.ui.theme.WarningAmber
import sg.edu.nus.iss.canmakan.shared.util.toScanHistoryDisplayString

// Screen for adding a new family member profile. Collects a name,
// relationship, and optional email before creating the profile.
@Composable
fun CreateNewProfileScreen(
    activeProfile: DietaryProfile,
    activeRestrictions: List<String> = emptyList(),
    onMenuClick: () -> Unit,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onCreateClick: (name: String, relationship: String, email: String) -> Unit = { _, _, _ -> }
) {
    // Text field values are stored as state so the UI updates as the
    // person types and so the current values can be read when the
    // create button is pressed.
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Column {
                AppTopBar(onMenuClick = onMenuClick)
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
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        // Back navigation row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
            Spacer(modifier = Modifier.width(4.dp))
            Text("Back")
        }

        // Title and subtitle
        Text(
            text = "Create New Family Member",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Create a new CanMakan profile for a family member.",
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Family member name field (required)
        FormLabel(text = "Name of family member", isRequired = true)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("e.g. Alice") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Relationship field (required)
        FormLabel(text = "Relationship to Admin", isRequired = true)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = relationship,
            onValueChange = { relationship = it },
            placeholder = { Text("e.g. Daughter, Son, Parent") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Email field (optional, no red asterisk)
        FormLabel(text = "Email address", isRequired = false)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = { Text("e.g. alice@example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Fields marked * are required.",
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Cancel and create actions, side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancelClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onCreateClick(name, relationship, email) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7A4C)),
                modifier = Modifier.weight(1f)
            ) {
                Text("Create Member")
            }
        }
    }
}
}
}

// Displays a field label, adding a red asterisk after the text when
// the field is required.
@Composable
private fun FormLabel(text: String, isRequired: Boolean) {
    Row {
        Text(text = text, fontWeight = FontWeight.Medium)
        if (isRequired) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "*", color = Color.Red)
        } else {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "(optional)", color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
fun ScanHistoryRow(entry: ScanHistoryEntry, onClick: () -> Unit) {
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
                .background(statusAccentColor(entry.verdict))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(entry.product.productName, fontWeight = FontWeight.Medium)
            Text(
                "${entry.product.brand} \u00B7 ${entry.scannedAt.toScanHistoryDisplayString()}",
                color = TextSecondary
            )
            entry.aiExplanation?.let { note ->
                val noteColor = if (entry.verdict == ScanVerdict.UNSAFE) AvoidRed else WarningAmber
                Text(note, color = noteColor)
            }
        }
        Box(modifier = Modifier.padding(end = 12.dp)) {
            StatusBadge(status = entry.verdict)
        }
    }
}