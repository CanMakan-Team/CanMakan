package sg.edu.nus.iss.canmakan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import sg.edu.nus.iss.canmakan.features.product.scan.ScannerScreen
import sg.edu.nus.iss.canmakan.navigation.CanMakanNavGraph
import sg.edu.nus.iss.canmakan.navigation.ROUTE_REGISTRATION
import sg.edu.nus.iss.canmakan.shared.model.DietaryProfile
import sg.edu.nus.iss.canmakan.shared.ui.theme.CanMakanTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CanMakanTheme {
                CanMakanNavGraph()
//                CanMakanNavGraph(startDestination = ROUTE_REGISTRATION)
            }
        }
    }


    /**
     * Temporary solution to render the ScannerScreen directly, bypassing the NavGraph
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CanMakanTheme {

                // 1. Create a temporary dummy profile to satisfy the ScannerScreen requirements
                val dummyProfile = DietaryProfile(
                    id = 1L,
                    familyId = 1L,
                    profileName = "Test User",
                    relationship = "Self",
                    initials = "TU",
                    isPrimary = true
                )

                // 2. Render the ScannerScreen directly, bypassing the NavGraph
                ScannerScreen(
                    activeProfile = dummyProfile,
                    activeRestrictions = listOf("Halal", "Peanut Allergy"), // Temporary dummy restrictions
                    onMenuClick = { /* Do nothing temporarily */ },
                    onScanClick = { /* Do nothing temporarily */ },
                    onHistoryClick = { /* Do nothing temporarily */ }
                )

            }
        }

    }
     **/
}
