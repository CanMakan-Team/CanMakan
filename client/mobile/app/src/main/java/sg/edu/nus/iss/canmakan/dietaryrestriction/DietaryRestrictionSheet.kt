package sg.edu.nus.iss.canmakan.dietaryrestriction

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
import sg.edu.nus.iss.canmakan.data.DietaryOption
import sg.edu.nus.iss.canmakan.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.theme.TextSecondary

// Content shown in the "Edit dietary requirements" bottom sheet.
// Religious diet allows only one choice; allergies and specific diets
// allow more than one to be picked at the same time.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDietaryRequirementsSheet(
    profileName: String,
    profileRole: String,
    religiousOptions: List<DietaryOption>,
    allergyOptions: List<DietaryOption>,
    specificDietOptions: List<DietaryOption>,
    onCancel: () -> Unit,
    onSave: (List<DietaryOption>, List<DietaryOption>, List<DietaryOption>) -> Unit
) {
    // Local copies so a change only takes effect once "Save" is pressed.
    var religious by remember { mutableStateOf(religiousOptions) }
    var allergies by remember { mutableStateOf(allergyOptions) }
    var specificDiets by remember { mutableStateOf(specificDietOptions) }

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
        SingleChoiceRow(options = religious) { updated -> religious = updated }

        Spacer(modifier = Modifier.height(16.dp))
        Text("ALLERGIES & INTOLERANCES", color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        MultiChoiceGrid(options = allergies) { updated -> allergies = updated }

        Spacer(modifier = Modifier.height(16.dp))
        Text("SPECIFIC DIETS", color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        MultiChoiceGrid(options = specificDiets) { updated -> specificDiets = updated }

        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = { onSave(religious, allergies, specificDiets) },
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
private fun SingleChoiceRow(options: List<DietaryOption>, onChange: (List<DietaryOption>) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { index, option ->
            SelectableOptionCard(
                label = option.label,
                isSelected = option.isSelected,
                modifier = Modifier.weight(1f)
            ) {
                val updated = options.mapIndexed { i, item -> item.copy(isSelected = i == index) }
                onChange(updated)
            }
        }
    }
}

// Allergies and specific diets allow more than one to be picked at once,
// laid out as a two-column grid.
@Composable
private fun MultiChoiceGrid(options: List<DietaryOption>, onChange: (List<DietaryOption>) -> Unit) {
    val rows = options.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowOptions.forEach { option ->
                    val index = options.indexOf(option)
                    SelectableOptionCard(
                        label = option.label,
                        isSelected = option.isSelected,
                        modifier = Modifier.weight(1f)
                    ) {
                        val updated = options.mapIndexed { i, item ->
                            if (i == index) item.copy(isSelected = !item.isSelected) else item
                        }
                        onChange(updated)
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
