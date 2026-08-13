package sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.AuthenticatedDietaryOnboardingUiState
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.AuthenticatedDietaryOnboardingViewModel
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascot
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotPose
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotSize
import sg.edu.nus.iss.canmakan.shared.ui.theme.BorderSubtle
import sg.edu.nus.iss.canmakan.shared.ui.theme.CardWhite
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextPrimary
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

private const val CATEGORY_RELIGIOUS = "RELIGIOUS"
private const val CATEGORY_ALLERGEN = "ALLERGEN"
private const val CATEGORY_DIET = "DIET"

@Composable
fun AuthenticatedDietaryOnboardingRoute(
    onResolved: () -> Unit,
    viewModel: AuthenticatedDietaryOnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.beginPendingSetup()
    }
    LaunchedEffect(state.resolved) {
        if (state.resolved) onResolved()
    }

    AuthenticatedDietaryOnboardingScreen(
        state = state,
        onProfileNameChange = viewModel::updateProfileName,
        onToggle = viewModel::toggleRestriction,
        onRetry = viewModel::retryCatalog,
        onSaveRestrictions = viewModel::saveRestrictions,
        onDefer = viewModel::deferSetup,
    )
}

@Composable
fun AuthenticatedDietaryOnboardingScreen(
    state: AuthenticatedDietaryOnboardingUiState,
    onProfileNameChange: (String) -> Unit,
    onToggle: (Long) -> Unit,
    onRetry: () -> Unit,
    onSaveRestrictions: () -> Unit,
    onDefer: () -> Unit,
) {
    Scaffold(
        topBar = { OnboardingTopBar() },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDefer,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Set up later")
                    }
                    Button(
                        onClick = onSaveRestrictions,
                        enabled = !state.isSubmitting && !state.isLoadingCatalog,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Save Profile")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Set up your dietary profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "You can complete this later. Select the dietary options relevant to you, or continue without creating a profile.",
                color = TextSecondary,
            )

            OutlinedTextField(
                value = state.profileName,
                onValueChange = onProfileNameChange,
                label = { Text("Profile Name") },
                supportingText = {
                    Text("This is the name for your personal dietary profile.")
                },
                readOnly = !state.profileNameEditable,
                enabled = !state.isSubmitting,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            state.errorMessage?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (state.restrictions.isEmpty() && !state.isLoadingCatalog) {
                            OutlinedButton(onClick = onRetry) { Text("Retry") }
                        }
                    }
                }
            }

            if (state.isLoadingCatalog) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Loading dietary options…")
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    RestrictionSection(
                        heading = "RELIGIOUS",
                        restrictions = state.restrictions.filter { it.category == CATEGORY_RELIGIOUS },
                        selectedIds = state.selections.keys,
                        isSubmitting = state.isSubmitting,
                        onToggle = onToggle,
                        isSingleChoice = true
                    )
                    RestrictionSection(
                        heading = "ALLERGIES & INTOLERANCES",
                        restrictions = state.restrictions.filter { it.category == CATEGORY_ALLERGEN },
                        selectedIds = state.selections.keys,
                        isSubmitting = state.isSubmitting,
                        onToggle = onToggle
                    )
                    RestrictionSection(
                        heading = "SPECIFIC DIETS",
                        restrictions = state.restrictions.filter { it.category == CATEGORY_DIET },
                        selectedIds = state.selections.keys,
                        isSubmitting = state.isSubmitting,
                        onToggle = onToggle
                    )
                    val known = setOf(CATEGORY_RELIGIOUS, CATEGORY_ALLERGEN, CATEGORY_DIET)
                    RestrictionSection(
                        heading = "OTHER",
                        restrictions = state.restrictions.filterNot { it.category in known },
                        selectedIds = state.selections.keys,
                        isSubmitting = state.isSubmitting,
                        onToggle = onToggle
                    )
                }
            }
        }
    }
}

private val OptionCardHeight = 64.dp

@Composable
private fun RestrictionSection(
    heading: String,
    restrictions: List<DietaryRestriction>,
    selectedIds: Set<Long>,
    isSubmitting: Boolean,
    onToggle: (Long) -> Unit,
    isSingleChoice: Boolean = false
) {
    if (restrictions.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            heading,
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (isSingleChoice) {
            SingleChoiceRow(
                options = restrictions,
                selectedIds = selectedIds,
                enabled = !isSubmitting,
                onToggle = onToggle
            )
        } else {
            MultiChoiceGrid(
                options = restrictions,
                selectedIds = selectedIds,
                enabled = !isSubmitting,
                onToggle = onToggle
            )
        }
    }
}

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
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = PrimaryGreen,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
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

@Composable
private fun OnboardingTopBar() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CanMakanMascot(
                pose = CanMakanMascotPose.Wave,
                size = CanMakanMascotSize.Compact,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("CanMakan", fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}
