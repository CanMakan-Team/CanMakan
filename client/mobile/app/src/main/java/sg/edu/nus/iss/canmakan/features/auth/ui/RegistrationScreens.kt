package sg.edu.nus.iss.canmakan.features.auth.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sg.edu.nus.iss.canmakan.features.auth.RegistrationStep
import sg.edu.nus.iss.canmakan.features.auth.RegistrationUiState
import sg.edu.nus.iss.canmakan.features.auth.RegistrationViewModel
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.data.RegistrationFailureType
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascot
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotPose
import sg.edu.nus.iss.canmakan.shared.ui.CanMakanMascotSize
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.LightRedBackground
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextPrimary
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

@Composable
fun RegistrationRoute(
    onRegistrationAuthenticated: (AuthenticatedUser) -> Unit,
    onLoginRequired: (String) -> Unit,
    onCancel: () -> Unit = {},
    invitationToken: String? = null,
    viewModel: RegistrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(invitationToken) {
        viewModel.setInvitationToken(invitationToken)
    }
    LaunchedEffect(state.authenticatedUser) {
        state.authenticatedUser?.let(onRegistrationAuthenticated)
    }

    when (state.step) {
        RegistrationStep.ACCOUNT_INFORMATION -> AccountInformationScreen(
            state = state,
            onNameChange = viewModel::updateName,
            onEmailChange = viewModel::updateEmail,
            onPasswordChange = viewModel::updatePassword,
            onConfirmPasswordChange = viewModel::updateConfirmPassword,
            onCreateAccount = viewModel::createAccount,
            onLoginRequired = { onLoginRequired(state.email.trim()) },
            onCancel = onCancel,
        )

        RegistrationStep.COMPLETE -> AutomaticLoginFailureScreen(
            email = state.account?.email.orEmpty(),
            message = state.registrationError.orEmpty(),
            onLoginRequired = { onLoginRequired(state.account?.email.orEmpty()) },
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
    onCreateAccount: () -> Unit,
    onLoginRequired: () -> Unit,
    onCancel: () -> Unit,
) {
    RegistrationPage(
        title = "Create New Account",
        subtitle = "Join CanMakan and shop with more confidence.",
        bottomBar = {
            RegistrationActionRow {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onCreateAccount,
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Register")
                    }
                }
            }
        },
    ) {
        state.registrationError?.let {
            ErrorMessage(it)
            if (state.registrationFailureType == RegistrationFailureType.DUPLICATE_EMAIL) {
                TextButton(onClick = onLoginRequired) { Text("Log in here") }
            }
        }

        LabeledTextField(
            label = "Profile Name",
            value = state.name,
            onValueChange = onNameChange,
            placeholder = "eg. Sarah Abdullah",
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
            supportingText = state.emailError
                ?: if (state.emailLocked) {
                    "This invitation was sent to this email."
                } else {
                    null
                },
            enabled = !state.emailLocked && !state.isSubmitting,
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
private fun AutomaticLoginFailureScreen(
    email: String,
    message: String,
    onLoginRequired: () -> Unit,
) {
    RegistrationPage(
        title = "Account created",
        subtitle = "Your CanMakan account for $email is ready, but automatic sign-in did not complete.",
        bottomBar = {
            RegistrationActionRow {
                Button(
                    onClick = onLoginRequired,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("Go to Login")
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
        ErrorMessage(message)
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
            verticalAlignment = Alignment.CenterVertically,
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
    enabled: Boolean = true,
) {
    Column {
        Text(label, fontWeight = FontWeight.Medium, color = TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
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
