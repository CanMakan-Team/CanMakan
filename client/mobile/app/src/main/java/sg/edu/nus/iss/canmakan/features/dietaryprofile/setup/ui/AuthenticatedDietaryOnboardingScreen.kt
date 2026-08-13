package sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.AuthenticatedDietaryOnboardingUiState
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.AuthenticatedDietaryOnboardingViewModel
import sg.edu.nus.iss.canmakan.features.dietaryprofile.setup.data.ProfileRestrictionSeverity
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascot
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotPose
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotSize
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightRedBackground
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
        onSeverityChange = viewModel::setSeverity,
        onRetry = viewModel::retryCatalog,
        onCreateProfile = viewModel::createProfile,
        onDefer = viewModel::deferSetup,
    )
}

@Composable
fun AuthenticatedDietaryOnboardingScreen(
    state: AuthenticatedDietaryOnboardingUiState,
    onProfileNameChange: (String) -> Unit,
    onToggle: (Long) -> Unit,
    onSeverityChange: (Long, ProfileRestrictionSeverity) -> Unit,
    onRetry: () -> Unit,
    onCreateProfile: () -> Unit,
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
                        onClick = onCreateProfile,
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
                "These options come from the current CanMakan catalog. " +
                    "Select at least one, or continue without creating a profile.",
                color = TextSecondary,
            )
            OutlinedTextField(
                value = state.profileName,
                onValueChange = onProfileNameChange,
                label = { Text("Profile name") },
                placeholder = { Text("e.g. Sarah Abdullah") },
                enabled = !state.isSubmitting,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            state.errorMessage?.let { message ->
                Card(colors = CardDefaults.cardColors(containerColor = LightRedBackground)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(message, color = AvoidRed)
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
                RestrictionSection(
                    heading = "RELIGIOUS",
                    restrictions = state.restrictions.filter { it.category == CATEGORY_RELIGIOUS },
                    state = state,
                    onToggle = onToggle,
                    onSeverityChange = onSeverityChange,
                )
                RestrictionSection(
                    heading = "ALLERGIES & INTOLERANCES",
                    restrictions = state.restrictions.filter { it.category == CATEGORY_ALLERGEN },
                    state = state,
                    onToggle = onToggle,
                    onSeverityChange = onSeverityChange,
                )
                RestrictionSection(
                    heading = "SPECIFIC DIETS",
                    restrictions = state.restrictions.filter { it.category == CATEGORY_DIET },
                    state = state,
                    onToggle = onToggle,
                    onSeverityChange = onSeverityChange,
                )
                val known = setOf(CATEGORY_RELIGIOUS, CATEGORY_ALLERGEN, CATEGORY_DIET)
                RestrictionSection(
                    heading = "OTHER",
                    restrictions = state.restrictions.filterNot { it.category in known },
                    state = state,
                    onToggle = onToggle,
                    onSeverityChange = onSeverityChange,
                )
            }
        }
    }
}

@Composable
private fun RestrictionSection(
    heading: String,
    restrictions: List<DietaryRestriction>,
    state: AuthenticatedDietaryOnboardingUiState,
    onToggle: (Long) -> Unit,
    onSeverityChange: (Long, ProfileRestrictionSeverity) -> Unit,
) {
    if (restrictions.isEmpty()) return
    Text(heading, color = TextSecondary, style = MaterialTheme.typography.titleSmall)
    restrictions.forEach { restriction ->
        val severity = state.selections[restriction.id]
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !state.isSubmitting) { onToggle(restriction.id) },
            colors = CardDefaults.cardColors(
                containerColor = if (severity != null) {
                    LightGreenBackground
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (severity != null) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(restriction.displayName, fontWeight = FontWeight.Medium, color = TextPrimary)
                }
                if (severity != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = severity == ProfileRestrictionSeverity.STRICT_AVOID,
                            onClick = {
                                onSeverityChange(
                                    restriction.id,
                                    ProfileRestrictionSeverity.STRICT_AVOID,
                                )
                            },
                            label = { Text("Strict avoid") },
                        )
                        FilterChip(
                            selected = severity == ProfileRestrictionSeverity.INTOLERANCE,
                            onClick = {
                                onSeverityChange(
                                    restriction.id,
                                    ProfileRestrictionSeverity.INTOLERANCE,
                                )
                            },
                            label = { Text("Intolerance") },
                        )
                    }
                }
            }
        }
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
