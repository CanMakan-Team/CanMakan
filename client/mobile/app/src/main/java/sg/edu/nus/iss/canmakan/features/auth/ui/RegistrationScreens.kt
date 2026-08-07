package sg.edu.nus.iss.canmakan.features.auth.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun RegistrationRoute(
    onRegistrationComplete: () -> Unit,
    viewModel: RegistrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (state.step) {
        RegistrationStep.ACCOUNT_INFORMATION -> AccountInformationScreen(
            state = state,
            onEmailChange = viewModel::updateEmail,
            onPasswordChange = viewModel::updatePassword,
            onConfirmPasswordChange = viewModel::updateConfirmPassword,
            onNext = viewModel::continueToDietaryProfile,
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
            onContinue = onRegistrationComplete,
        )
    }
}

@Composable
private fun AccountInformationScreen(
    state: RegistrationUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    RegistrationPage(stepLabel = "Step 1 of 2", title = "Create your account") {
        Text(
            text = "Enter your account information. Your dietary preferences come next.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.registrationError?.let { ErrorMessage(it) }

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            isError = state.emailError != null,
            supportingText = { state.emailError?.let { Text(it) } },
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = state.passwordError != null,
            supportingText = { state.passwordError?.let { Text(it) } },
        )

        OutlinedTextField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm password") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = state.confirmPasswordError != null,
            supportingText = { state.confirmPasswordError?.let { Text(it) } },
        )

        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Next")
        }
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
    RegistrationPage(stepLabel = "Step 2 of 2", title = "Dietary profile (optional)") {
        if (state.account == null) {
            OutlinedButton(onClick = onBack, enabled = !state.isSubmitting) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Text("Back")
            }

            Text(
                text = "Choose any dietary restrictions you want to set up. " +
                    "You can also leave this blank and add them later.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> state.availableRestrictions.forEach { restriction ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.isSubmitting) {
                                onRestrictionToggle(restriction.id)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = restriction.id in state.selectedRestrictionIds,
                            onCheckedChange = { onRestrictionToggle(restriction.id) },
                            enabled = !state.isSubmitting,
                        )
                        Column {
                            Text(restriction.displayName, fontWeight = FontWeight.Medium)
                            restriction.description?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onCreateAccount,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
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
        } else if (state.profileSetupStatus == ProfileSetupStatus.DEFERRED_UNAVAILABLE) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Account created", fontWeight = FontWeight.Bold)
                    Text(state.profileSetupMessage.orEmpty())
                    Text(
                        "Your dietary selections were not saved. Your account remains " +
                            "active, and you can set up your profile later."
                    )
                }
            }

            Button(onClick = onSetUpLater, modifier = Modifier.fillMaxWidth()) {
                Text("Set up profile later")
            }
        }
    }
}

@Composable
private fun RegistrationCompleteScreen(
    email: String,
    onContinue: () -> Unit,
) {
    RegistrationPage(stepLabel = "Complete", title = "Account created") {
        Text("Your CanMakan account for $email is ready.")
        Text(
            "Dietary profile setup remains separate and can be completed later.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

@Composable
private fun RegistrationPage(
    stepLabel: String,
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "CanMakan",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stepLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )
    }
}
