package sg.edu.nus.iss.canmakan.features.family.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import sg.edu.nus.iss.canmakan.features.family.model.RelationshipToAdmin
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab
import sg.edu.nus.iss.canmakan.shared.ui.theme.AvoidRed
import sg.edu.nus.iss.canmakan.shared.ui.theme.PrimaryGreen
import sg.edu.nus.iss.canmakan.shared.ui.theme.TextSecondary

/** Creates a dependant dietary profile (no login) via POST /families/me/profiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDependantProfileScreen(
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onCreated: () -> Unit = {},
    viewModel: CreateDependantProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.created) {
        if (uiState.created) {
            Toast.makeText(context, "Dependant profile created successfully.", Toast.LENGTH_SHORT).show()
            delay(1500)
            onCreated()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                onMenuClick = onMenuClick,
                onNotificationsClick = onNotificationsClick,
            )
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onBackClick() }
                    .padding(bottom = 24.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back")
            }

            Text(
                text = "Add dependant profile",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Create a dependant profile without a login. Restrictions can be edited later.",
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            DependantFormLabel(text = "Name of family member", isRequired = true)
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

            DependantFormLabel(text = "Relationship to Admin", isRequired = true)
            Spacer(modifier = Modifier.height(6.dp))
            var relationshipMenuExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = relationshipMenuExpanded,
                onExpandedChange = { expanded ->
                    relationshipMenuExpanded = expanded && !uiState.isSubmitting
                },
            ) {
                OutlinedTextField(
                    value = uiState.relationship?.displayName.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select relationship") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = relationshipMenuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = !uiState.isSubmitting,
                        ),
                    singleLine = true,
                    enabled = !uiState.isSubmitting,
                )
                ExposedDropdownMenu(
                    expanded = relationshipMenuExpanded,
                    onDismissRequest = { relationshipMenuExpanded = false },
                ) {
                    RelationshipToAdmin.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            onClick = {
                                viewModel.updateRelationship(option)
                                relationshipMenuExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Fields marked * are required.",
                color = TextSecondary,
                fontSize = 13.sp,
            )

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = message, color = AvoidRed)
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSubmitting,
                ) {
                    Text(if (uiState.isSubmitting) "Creating…" else "Create profile")
                }
            }
        }
    }
}

@Composable
private fun DependantFormLabel(text: String, isRequired: Boolean) {
    Row {
        Text(text = text, fontWeight = FontWeight.Medium)
        if (isRequired) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "*", color = AvoidRed)
        }
    }
}
