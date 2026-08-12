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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import sg.edu.nus.iss.canmakan.features.auth.RegistrationStep
import sg.edu.nus.iss.canmakan.features.auth.RegistrationUiState
import sg.edu.nus.iss.canmakan.features.auth.RegistrationViewModel
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascot
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotPose
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotSize
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightGreenBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightRedBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextPrimary
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

@Composable
fun RegistrationRoute(
    onRegistrationComplete: () -> Unit,
    onCancel: () -> Unit = {},
    invitationToken: String? = null,
    viewModel: RegistrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(invitationToken) {
        viewModel.setInvitationToken(invitationToken)
    }

    when (state.step) {
        RegistrationStep.ACCOUNT_INFORMATION -> AccountInformationScreen(
            state = state,
            onEmailChange = viewModel::updateEmail,
            onPasswordChange = viewModel::updatePassword,
            onConfirmPasswordChange = viewModel::updateConfirmPassword,
            onNext = viewModel::continueToDietaryProfile,
            onCancel = onCancel,
        )

        RegistrationStep.OPTIONAL_DIETARY_PROFILE -> OptionalDietaryIntentScreen(
            state = state,
            onBack = viewModel::backToAccountInformation,
            onDietarySetupRequested = viewModel::setDietarySetupRequested,
            onCreateAccount = viewModel::createAccount,
        )

        RegistrationStep.COMPLETE -> RegistrationCompleteScreen(
            email = state.account?.email.orEmpty(),
            dietarySetupRequested = state.wantsDietarySetup,
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
private fun OptionalDietaryIntentScreen(
    state: RegistrationUiState,
    onBack: () -> Unit,
    onDietarySetupRequested: (Boolean) -> Unit,
    onCreateAccount: () -> Unit,
) {
    RegistrationPage(
        title = "Optional Dietary Setup",
        subtitle = "Choose whether to configure dietary restrictions after you sign in. " +
            "Your account is created separately and you can always configure them later.",
        bottomBar = {
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
        },
    ) {
        OnboardingChoice(
            title = "Continue dietary setup after sign-in",
            description = "Sign in first, then choose from the current CanMakan restriction catalog.",
            selected = state.wantsDietarySetup,
            enabled = !state.isSubmitting,
            onClick = { onDietarySetupRequested(true) },
        )
        OnboardingChoice(
            title = "Skip for now",
            description = "Create only your account. No dietary profile will be created.",
            selected = !state.wantsDietarySetup,
            enabled = !state.isSubmitting,
            onClick = { onDietarySetupRequested(false) },
        )
    }
}

@Composable
private fun OnboardingChoice(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) LightGreenBackground else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryGreen)
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(description, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun RegistrationCompleteScreen(
    email: String,
    dietarySetupRequested: Boolean,
    onContinue: () -> Unit,
) {
    RegistrationPage(
        title = "Account created",
        subtitle = "Your CanMakan account for $email is ready.",
        bottomBar = {
            RegistrationActionRow {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue to Sign In")
                }
            }
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CanMakanMascot(
                pose = CanMakanMascotPose.Wave,
                size = CanMakanMascotSize.Large,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = if (dietarySetupRequested) {
                "Sign in explicitly to choose and save your dietary restrictions."
            } else {
                "No dietary profile was created. You can configure one later."
            },
            color = TextSecondary,
        )
    }
}

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

@Composable
private fun RegistrationTopBar() {
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

@Composable
private fun RegistrationActionRow(content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
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
            supportingText = supportingText?.let {
                {
                    Text(
                        text = it,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LightRedBackground),
    ) {
        Text(text = message, color = AvoidRed, modifier = Modifier.padding(16.dp))
    }
}
