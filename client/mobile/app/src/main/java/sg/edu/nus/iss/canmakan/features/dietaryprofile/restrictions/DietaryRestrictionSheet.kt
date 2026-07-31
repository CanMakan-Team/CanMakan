package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions

import android.R.attr.onClick
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

// Content shown in the "Edit dietary requirements" bottom sheet.
// Religious diet allows only one choice; allergies and specific diets
// allow more than one to be picked at the same time.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietaryRestrictionSheet(
    profileName: String,
    profileRole: String,
    viewModel: DietaryRestrictionViewModel = hiltViewModel(),
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadDietaryRestrictions()
    }
    // read current UI state from ViewModel and rerun whenever state changes
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.padding(20.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    "Edit dietary requirements",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text("$profileName \u00B7 $profileRole", color = TextSecondary)
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("RELIGIOUS", color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceRow(
            options = uiState.value.religiousRestrictions,
            selectedIds = uiState.value.selectedRestrictions.keys
        ) { selectedId -> viewModel.selectReligiousRestriction(selectedId) }

        Spacer(modifier = Modifier.height(16.dp))
        Text("ALLERGIES & INTOLERANCES", color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        MultiChoiceGrid(
            options = uiState.value.allergenRestrictions,
            selectedIds = uiState.value.selectedRestrictions.keys
        ) { selectedId -> viewModel.toggleDietaryRestriction(selectedId) }

        Spacer(modifier = Modifier.height(16.dp))
        Text("SPECIFIC DIETS", color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        MultiChoiceGrid(
            options = uiState.value.dietRestrictions,
            selectedIds = uiState.value.selectedRestrictions.keys
        ) { selectedId -> viewModel.toggleDietaryRestriction(selectedId) }

        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                // Uncomment the below code when backend database is up
                onClick = {
                    viewModel.onSave()
                    onSave()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text("Save")
            }
        }
    }
}

// Religious diet is a single choice, shown as two options side by side.
@Composable
private fun SingleChoiceRow(
    options: List<DietaryRestriction>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { index, option ->
            SelectableOptionCard(
                label = option.displayName,
                isSelected = selectedIds.contains(option.id),
                modifier = Modifier.weight(1f)
            ) {
                onToggle(option.id)
            }
        }
    }
}

// Allergies and specific diets allow more than one to be picked at once,
// laid out as a two-column grid.
@Composable
private fun MultiChoiceGrid(
    options: List<DietaryRestriction>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit
) {
    val rows = options.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowOptions.forEach { option ->
                    val index = options.indexOf(option)
                    SelectableOptionCard(
                        label = option.displayName,
                        isSelected = selectedIds.contains(option.id),
                        modifier = Modifier.weight(1f)
                    ) {
                        onToggle(option.id)
                    }
                }
                if (rowOptions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SelectableOptionCard(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) LightGreenBackground else Color.White)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryGreen)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(label, color = if (isSelected) PrimaryGreen else Color.Black)
    }
}
