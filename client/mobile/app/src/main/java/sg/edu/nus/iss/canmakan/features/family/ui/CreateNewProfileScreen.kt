package sg.edu.nus.iss.canmakan.features.family.ui

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/** Creates a dependant dietary profile (no login) via POST /families/me/profiles. */
@Composable
fun CreateNewProfileScreen(
    activeProfile: DietaryProfile,
    onMenuClick: () -> Unit,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onCreated: () -> Unit = {},
    viewModel: CreateNewProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.created) {
        if (uiState.created) {
            onCreated()
        }
    }

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
                onHistoryClick = onHistoryClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onBackClick() }
                    .padding(bottom = 24.dp),
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back")
            }

            Text(
                text = "Create New Family Member",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Create a dependant profile without a login. Restrictions can be edited later.",
                color = Color.Gray,
            )

            Spacer(modifier = Modifier.height(24.dp))

            FormLabel(text = "Name of family member", isRequired = true)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = uiState.profileName,
                onValueChange = viewModel::updateProfileName,
                placeholder = { Text("e.g. Alice") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSubmitting,
            )

            Spacer(modifier = Modifier.height(20.dp))

            FormLabel(text = "Relationship to Admin", isRequired = true)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = uiState.relationship,
                onValueChange = viewModel::updateRelationship,
                placeholder = { Text("e.g. CHILD, PARENT, OTHER") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSubmitting,
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Fields marked * are required.",
                color = Color.Gray,
                fontSize = 13.sp,
            )

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = message, color = Color.Red)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSubmitting,
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = viewModel::create,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7A4C)),
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSubmitting,
                ) {
                    Text(if (uiState.isSubmitting) "Creating…" else "Create Member")
                }
            }
        }
    }
}

@Composable
private fun FormLabel(text: String, isRequired: Boolean) {
    Row {
        Text(text = text, fontWeight = FontWeight.Medium)
        if (isRequired) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "*", color = Color.Red)
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(56.dp)
                .background(statusAccentColor(entry.verdict)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(entry.product.productName, fontWeight = FontWeight.Medium)
            Text(
                "${entry.product.brand} \u00B7 ${entry.scannedAt.toScanHistoryDisplayString()}",
                color = TextSecondary,
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
