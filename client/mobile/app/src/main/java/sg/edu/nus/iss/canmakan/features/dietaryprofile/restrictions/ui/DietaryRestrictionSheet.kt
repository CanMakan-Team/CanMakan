package sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.DietaryRestrictionViewModel
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.shared.ui.theme.BorderSubtle
import sg.edu.nus.iss.canmakan.shared.ui.theme.CardWhite
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextPrimary
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

/** Fixed height so one-line and two-line labels share the same touch target. */
private val OptionCardHeight = 64.dp

// Content shown in the "Edit dietary requirements" bottom sheet.
// Religious diet allows only one choice; allergies and specific diets
// allow more than one to be picked at the same time.
// D3: non-admins viewing another member's profile see a read-only sheet.
@Composable
fun DietaryRestrictionSheet(
    profileName: String,
    profileRole: String,
    viewModel: DietaryRestrictionViewModel = hiltViewModel(),
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // read current UI state from ViewModel and rerun whenever state changes
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // null while resolving → treat as view-only so dismiss shows "Close", not "Cancel"
    val allowEdit = uiState.allowRestrictionEdit == true

    // Near-full expanded height; LazyColumn nested-scroll lets drag-down dismiss when at top.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (allowEdit) "Edit dietary restrictions" else "View dietary restrictions",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                val profileSubtitle = if (profileRole.isBlank()) {
                    profileName
                } else {
                    "$profileName \u00B7 $profileRole"
                }
                Text(profileSubtitle, color = TextSecondary)
            }

            uiState.restrictionEditHint?.let { hint ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = hint,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "RELIGIOUS",
                            color = TextSecondary,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceRow(
                            options = uiState.religiousRestrictions,
                            selectedIds = uiState.selectedRestrictions.keys,
                            enabled = allowEdit,
                        ) { selectedId -> viewModel.selectReligiousRestriction(selectedId) }
                    }
                }
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "ALLERGIES & INTOLERANCES",
                            color = TextSecondary,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        MultiChoiceGrid(
                            options = uiState.allergenRestrictions,
                            selectedIds = uiState.selectedRestrictions.keys,
                            enabled = allowEdit,
                        ) { selectedId -> viewModel.toggleDietaryRestriction(selectedId) }
                    }
                }
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "SPECIFIC DIETS",
                            color = TextSecondary,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        MultiChoiceGrid(
                            options = uiState.dietRestrictions,
                            selectedIds = uiState.selectedRestrictions.keys,
                            enabled = allowEdit,
                        ) { selectedId -> viewModel.toggleDietaryRestriction(selectedId) }
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(if (allowEdit) "Cancel" else "Close")
                }
                if (allowEdit) {
                    Button(
                        onClick = {
                            viewModel.onSave {
                                Toast.makeText(
                                    context,
                                    "Dietary restrictions saved successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onSave()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                        enabled = !uiState.isLoading
                    ) {
                        Text("Save")
                    }
                }
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(CardWhite.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryGreen)
            }
        }
    }
}

// Religious diet is a single choice, shown as two options side by side.
@Composable
private fun SingleChoiceRow(
    options: List<DietaryRestriction>,
    selectedIds: Set<Long>,
    enabled: Boolean,
    onToggle: (Long) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { option ->
            SelectableOptionCard(
                label = option.displayName,
                isSelected = selectedIds.contains(option.id),
                enabled = enabled,
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
    enabled: Boolean,
    onToggle: (Long) -> Unit
) {
    val rows = options.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowOptions ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowOptions.forEach { option ->
                    SelectableOptionCard(
                        label = option.displayName,
                        isSelected = selectedIds.contains(option.id),
                        enabled = enabled,
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
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(OptionCardHeight)
            .clip(shape)
            .background(if (isSelected) LightGreenBackground else CardWhite)
            .border(
                width = 1.dp,
                color = if (isSelected) PrimaryGreen else BorderSubtle,
                shape = shape,
            )
            .alpha(if (enabled) 1f else 0.55f)
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp)
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = PrimaryGreen,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = if (isSelected) PrimaryGreen else TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
