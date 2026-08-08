package sg.edu.nus.iss.canmakan.features.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sg.edu.nus.iss.canmakan.features.auth.ProfileSetupStatus
import sg.edu.nus.iss.canmakan.features.auth.RegistrationStep
import sg.edu.nus.iss.canmakan.features.auth.RegistrationUiState
import sg.edu.nus.iss.canmakan.features.auth.RegistrationViewModel
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.model.DietaryRestriction
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightRedBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextPrimary
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

// Dietary restriction categories returned by the backend. Used to group
// options into the same sections shown in the Edit Dietary Requirements
// sheet, so registration and profile editing look and behave the same way.
private const val CATEGORY_RELIGIOUS = "RELIGIOUS"
private const val CATEGORY_ALLERGEN = "ALLERGEN"
private const val CATEGORY_DIET = "DIET"

@Composable
fun RegistrationRoute(
    onRegistrationComplete: () -> Unit,
    onCancel: () -> Unit = {},
    viewModel: RegistrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (state.step) {
        RegistrationStep.ACCOUNT_INFORMATION -> AccountInformationScreen(
            state = state,
            onNameChange = viewModel::updateName,
            onEmailChange = viewModel::updateEmail,
            onPasswordChange = viewModel::updatePassword,
            onConfirmPasswordChange = viewModel::updateConfirmPassword,
            onNext = viewModel::continueToDietaryProfile,
            onCancel = onCancel,
        )

        RegistrationStep.OPTIONAL_DIETARY_PROFILE -> OptionalDietaryProfileScreen(
            state = state,
            onBack = viewModel::backToAccountInformation,
            onRestrictionToggle = viewModel::toggleRestriction,
            onCreateAccount = viewModel::createAccount,
            onSetUpLater = viewModel::completeProfileSetupLater,
        )

        RegistrationStep.COMPLETE -> RegistrationCompleteScreen(
            email = state.account?.email.orEmpty(),
            dietaryProfileConfigured = state.profileSetupStatus == ProfileSetupStatus.SELECTED,
            onContinue = onRegistrationComplete,
        )
    }
}

@Composable
private fun AccountInformationScreen(
    state: RegistrationUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit,
) {
    RegistrationPage(
        title = "Create New Account",
        subtitle = "Set up your CanMakan login credentials.",
        bottomBar = {
            RegistrationActionRow {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                    Text("Next")
                }
            }
        },
    ) {
        state.registrationError?.let { ErrorMessage(it) }

        LabeledTextField(
            label = "Name",
            value = state.name,
            onValueChange = onNameChange,
            placeholder = "e.g. Sarah Abdullah",
            keyboardType = KeyboardType.Text,
            isError = state.nameError != null,
            supportingText = state.nameError,
        )

        LabeledTextField(
            label = "Email address",
            value = state.email,
            onValueChange = onEmailChange,
            placeholder = "e.g. sarah@example.com",
            keyboardType = KeyboardType.Email,
            isError = state.emailError != null,
            supportingText = state.emailError,
        )

        LabeledTextField(
            label = "Password",
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = "Enter a strong password",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            isError = state.passwordError != null,
            supportingText = state.passwordError,
        )

        LabeledTextField(
            label = "Retype password",
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            placeholder = "Re-enter your password",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            isError = state.confirmPasswordError != null,
            supportingText = state.confirmPasswordError,
        )
    }
}

@Composable
private fun OptionalDietaryProfileScreen(
    state: RegistrationUiState,
    onBack: () -> Unit,
    onRestrictionToggle: (Long) -> Unit,
    onCreateAccount: () -> Unit,
    onSetUpLater: () -> Unit,
) {
    RegistrationPage(
        title = "Customize Dietary Restriction Profile",
        subtitle = if (state.account == null) {
            "Choose any dietary restrictions you want to set up. " +
                "You can also leave this blank and add them later."
        } else {
            null
        },
        bottomBar = {
            if (state.account == null) {
                RegistrationActionRow {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = onCreateAccount,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Create Account")
                        }
                    }
                }
            } else if (state.profileSetupStatus == ProfileSetupStatus.DEFERRED_UNAVAILABLE) {
                RegistrationActionRow {
                    Button(onClick = onSetUpLater, modifier = Modifier.fillMaxWidth()) {
                        Text("Set up profile later")
                    }
                }
            }
        },
    ) {
        if (state.account == null) {
            when {
                state.dietaryOptionsLoading -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Loading dietary options…")
                }

                state.dietaryOptionsError != null -> Text(
                    text = state.dietaryOptionsError,
                    color = TextSecondary,
                )

                else -> {
                    val religious = state.availableRestrictions.filter { it.category == CATEGORY_RELIGIOUS }
                    val allergens = state.availableRestrictions.filter { it.category == CATEGORY_ALLERGEN }
                    val diets = state.availableRestrictions.filter { it.category == CATEGORY_DIET }
                    // Anything outside the three known categories is still shown, rather
                    // than silently dropped, in case the backend adds a new category.
                    val others = state.availableRestrictions.filterNot {
                        it.category in setOf(CATEGORY_RELIGIOUS, CATEGORY_ALLERGEN, CATEGORY_DIET)
                    }

                    RestrictionSection(
                        heading = "RELIGIOUS",
                        options = religious,
                        selectedIds = state.selectedRestrictionIds,
                        enabled = !state.isSubmitting,
                        onToggle = onRestrictionToggle,
                    )
                    RestrictionSection(
                        heading = "ALLERGIES & INTOLERANCES",
                        options = allergens,
                        selectedIds = state.selectedRestrictionIds,
                        enabled = !state.isSubmitting,
                        onToggle = onRestrictionToggle,
                    )
                    RestrictionSection(
                        heading = "SPECIFIC DIETS",
                        options = diets,
                        selectedIds = state.selectedRestrictionIds,
                        enabled = !state.isSubmitting,
                        onToggle = onRestrictionToggle,
                    )
                    if (others.isNotEmpty()) {
                        RestrictionSection(
                            heading = "OTHER",
                            options = others,
                            selectedIds = state.selectedRestrictionIds,
                            enabled = !state.isSubmitting,
                            onToggle = onRestrictionToggle,
                        )
                    }
                }
            }
        } else if (state.profileSetupStatus == ProfileSetupStatus.DEFERRED_UNAVAILABLE) {
            // Only reached when the account was created but saving the selected
            // restrictions genuinely failed (e.g. a network error) — not merely
            // because restrictions were selected.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LightRedBackground),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Dietary restrictions not saved", fontWeight = FontWeight.Bold, color = AvoidRed)
                    Text(state.profileSetupMessage.orEmpty(), color = AvoidRed)
                    Text(
                        "Your account remains active. You can set up your dietary " +
                            "restrictions again later.",
                        color = AvoidRed,
                    )
                }
            }
        }
    }
}

@Composable
private fun RegistrationCompleteScreen(
    email: String,
    dietaryProfileConfigured: Boolean,
    onContinue: () -> Unit,
) {
    RegistrationPage(
        title = if (dietaryProfileConfigured) {
            "Account and dietary profile created"
        } else {
            "Account created"
        },
        subtitle = if (dietaryProfileConfigured) {
            "Your CanMakan account and dietary profile for $email are ready."
        } else {
            "Your CanMakan account for $email is ready."
        },
        bottomBar = {
            RegistrationActionRow {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }
        },
    ) {
        Text(
            text = if (dietaryProfileConfigured) {
                "Your selected dietary restrictions have been saved."
            } else {
                "Dietary profile setup remains separate and can be completed later."
            },
            color = TextSecondary,
        )
    }
}

// A labelled group of selectable restriction options, laid out as a
// two-column grid of pill-shaped cards. Skips rendering entirely when
// there are no options in this category, e.g. while data is still loading.
@Composable
private fun RestrictionSection(
    heading: String,
    options: List<DietaryRestriction>,
    selectedIds: Set<Long>,
    enabled: Boolean,
    onToggle: (Long) -> Unit,
) {
    if (options.isEmpty()) return

    Column {
        Text(heading, color = TextSecondary, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowOptions.forEach { option ->
                    RestrictionOptionCard(
                        label = option.displayName,
                        isSelected = option.id in selectedIds,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onToggle(option.id) },
                    )
                }
                if (rowOptions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// Matches the SelectableOptionCard look used in the Edit Dietary
// Requirements sheet: a rounded pill that fills with the light green
// background and shows a check mark once selected.
@Composable
private fun RestrictionOptionCard(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) LightGreenBackground else MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
    ) {
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryGreen)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(label, color = if (isSelected) PrimaryGreen else TextPrimary)
    }
}

// Shared page frame for every registration step: a top bar that only
// shows the CanMakan brand mark, a scrollable content column, and an
// optional pinned action row at the bottom. Deliberately does not include
// AppTopBar's menu/notification icons or AppBottomNavBar, since account
// creation happens before a profile (and therefore a drawer or nav
// destination to switch between) exists.
@Composable
private fun RegistrationPage(
    title: String,
    subtitle: String? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = { RegistrationTopBar() },
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
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
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            subtitle?.let { Text(text = it, color = TextSecondary) }
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

// Minimal top bar for the registration flow: just the CanMakan brand
// mark, centered, with no menu button and no notification bell, since
// there is nothing yet to open a drawer or show notifications for.
@Composable
private fun RegistrationTopBar() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("CanMakan", fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

// A bottom bar row pinned above the system navigation area, used for the
// primary action(s) of each registration step (Cancel/Next, Back/Create
// Account, Continue). navigationBarsPadding() keeps the buttons clear of
// the system gesture bar instead of sitting flush against it.
@Composable
private fun RegistrationActionRow(content: @Composable RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

// A field label above an outlined text field, matching the labelled-form
// pattern used elsewhere in the app (see CreateNewProfileScreen).
@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    Column {
        Text(label, fontWeight = FontWeight.Medium, color = TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            singleLine = true,
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
        )
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightRedBackground),
    ) {
        Text(
            text = message,
            color = AvoidRed,
            modifier = Modifier.padding(16.dp),
        )
    }
}
