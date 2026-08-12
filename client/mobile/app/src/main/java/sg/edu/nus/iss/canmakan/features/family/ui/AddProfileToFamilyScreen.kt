package sg.edu.nus.iss.canmakan.features.family.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.ActiveProfileChip
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab

@Composable
fun AddProfileToFamilyScreen(
    activeProfile: DietaryProfile?,
    activeRestrictions: List<String> = emptyList(),
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onInviteCreated: () -> Unit = {},
    viewModel: AddProfileToFamilyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val result = uiState.searchResult
    val canInvite = result != null &&
        result.familyLinkStatus != "ALREADY_LINKED" &&
        result.familyLinkStatus != "PENDING"

    Scaffold(
        topBar = {
            Column {
                AppTopBar(onMenuClick = onMenuClick)
                activeProfile?.let { ActiveProfileChip(profile = it) }
            }
        },
        bottomBar = {
            AppBottomNavBar(
                selectedTab = BottomTab.SCAN,
                onScanClick = onScanClick,
                onHistoryClick = onHistoryClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onBackClick() }
                    .padding(bottom = 24.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back")
            }

            Text(
                text = "Invite to Family",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Invite a registered CanMakan user or someone who does not have an account yet. They join when they register or sign in with this email.",
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            FormLabel(text = "Email address", isRequired = true)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::updateEmail,
                placeholder = { Text("e.g. member@example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSearching && !uiState.isInviting
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { viewModel.search() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7A4C)),
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSearching && !uiState.isInviting
                ) {
                    Text(if (uiState.isSearching) "Searching…" else "Search")
                }
            }

            if (result != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = result.displayName ?: if (result.accountStatus == "NOT_REGISTERED") {
                        "Not registered yet"
                    } else {
                        "CanMakan user"
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = result.maskedEmail, color = Color.Gray)
                Text(text = "Account: ${result.accountStatus}", color = Color.Gray)
                Text(text = "Link: ${result.familyLinkStatus}", color = Color.Gray)

                if (canInvite) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.createInvite() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7A4C)),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isInviting
                    ) {
                        Text(if (uiState.isInviting) "Creating invite…" else "Create invite")
                    }
                }
            }

            uiState.invitation?.let { invitation ->
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "Invitation ready", fontWeight = FontWeight.Bold)
                Text(text = invitation.inviteUrl, color = Color.Gray)
                Text(text = "Code: ${invitation.inviteCode}", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val appDeepLink = "canmakan://invite/${invitation.invitationToken}"
                        val shareText =
                            "Join my CanMakan family:\n$appDeepLink\n" +
                                "Web: ${invitation.inviteUrl}\nCode: ${invitation.inviteCode}"
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share invitation"))
                        onInviteCreated()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7A4C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share invitation")
                }
            }

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = message, color = Color.Red)
            }
        }
    }
}

@Composable
private fun FormLabel(text: String, isRequired: Boolean) {
    Row {
        Text(text = text, fontWeight = FontWeight.Medium)
        if (isRequired) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = "*", color = Color.Red)
        }
    }
}
