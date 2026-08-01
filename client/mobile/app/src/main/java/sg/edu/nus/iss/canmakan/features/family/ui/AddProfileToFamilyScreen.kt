package sg.edu.nus.iss.canmakan.features.family.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.ActiveProfileChip
import sg.edu.nus.iss.canmakan.shared.ui.AppBottomNavBar
import sg.edu.nus.iss.canmakan.shared.ui.AppTopBar
import sg.edu.nus.iss.canmakan.shared.ui.BottomTab

// Screen for linking an existing CanMakan user to the current family,
// using either their User ID or their registered email address.
@Composable
fun AddProfileToFamilyScreen(
    activeProfile: DietaryProfile,
    activeRestrictions: List<String> = emptyList(),
    onMenuClick: () -> Unit,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onAddProfileClick: (userId: String, email: String) -> Unit
) {
    var userId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Column {
                AppTopBar(onMenuClick = onMenuClick)
                ActiveProfileChip(profile = activeProfile)
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
                .padding(20.dp)
        ) {
            // Back navigation row
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

            // Title and subtitle
            Text(
                text = "Add Profile to Family",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add an existing CanMakan user to your family by their User ID and email address.",
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // User ID field, with a caption underneath explaining where to find it
            FormLabel(text = "User ID", isRequired = true)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = userId,
                onValueChange = { userId = it },
                placeholder = { Text("e.g. CMK-123456") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "The User ID can be found in the other person's CanMakan profile.",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Email field
            FormLabel(text = "Email address", isRequired = true)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("e.g. member@example.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Either of the fields marked * is required.",
                color = Color.Gray,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Cancel and add profile actions, side by side
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
                    onClick = { onAddProfileClick(userId, email) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E7A4C)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add Profile")
                }
            }
        }
    }
}

// Displays a field label, adding a red asterisk after the text when
// the field is required.
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
