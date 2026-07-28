package com.example.canmakan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.canmakan.data.SampleData
import com.example.canmakan.ui.components.ProfileDrawerContent
import com.example.canmakan.ui.screens.EditDietaryRequirementsSheet
import com.example.canmakan.ui.screens.HistoryScreen
import com.example.canmakan.ui.screens.ProductDetailScreen
import com.example.canmakan.ui.screens.ScannerScreen
import com.example.canmakan.ui.theme.CanMakanTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CanMakanTheme {
                CanMakanApp()
            }
        }
    }
}

private const val ROUTE_SCANNER = "scanner"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_PRODUCT_DETAIL = "product_detail"

// The top-level screen. It wires together the navigation between the
// three screens, the side drawer, and the edit dietary requirements sheet.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanMakanApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var activeProfile by remember { mutableStateOf(SampleData.profiles.first()) }
    var showEditDietarySheet by remember { mutableStateOf(false) }

    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawer() = scope.launch { drawerState.close() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ProfileDrawerContent(
                    profiles = SampleData.profiles,
                    activeProfile = activeProfile,
                    onProfileSelected = { selected ->
                        activeProfile = selected
                        closeDrawer()
                    },
                    onEditDietaryClick = {
                        closeDrawer()
                        showEditDietarySheet = true
                    },
                    onScannerClick = {
                        closeDrawer()
                        navController.navigate(ROUTE_SCANNER)
                    },
                    onHistoryClick = {
                        closeDrawer()
                        navController.navigate(ROUTE_HISTORY)
                    },
                    onSignOutClick = { closeDrawer() },
                    onCloseClick = { closeDrawer() }
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = ROUTE_SCANNER) {
            composable(ROUTE_SCANNER) {
                ScannerScreen(
                    activeProfile = activeProfile,
                    activeRestrictions = listOf("Halal", "Low Sugar"),
                    onMenuClick = { openDrawer() },
                    onScanClick = { navController.navigate(ROUTE_PRODUCT_DETAIL) },
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) }
                )
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    activeProfile = activeProfile,
                    entries = SampleData.scanHistory,
                    onMenuClick = { openDrawer() },
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { },
                    onEntryClick = { navController.navigate(ROUTE_PRODUCT_DETAIL) }
                )
            }
            composable(ROUTE_PRODUCT_DETAIL) {
                ProductDetailScreen(
                    product = SampleData.scannedProduct,
                    flags = SampleData.productFlags,
                    alternatives = SampleData.alternatives,
                    profileName = activeProfile.name,
                    onBackClick = { navController.popBackStack() },
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) }
                )
            }
        }

        if (showEditDietarySheet) {
            ModalBottomSheet(onDismissRequest = { showEditDietarySheet = false }) {
                EditDietaryRequirementsSheet(
                    profileName = activeProfile.name,
                    profileRole = activeProfile.role,
                    religiousOptions = SampleData.religiousOptions(),
                    allergyOptions = SampleData.allergyOptions(),
                    specificDietOptions = SampleData.specificDietOptions(),
                    onCancel = { showEditDietarySheet = false },
                    onSave = { _, _, _ -> showEditDietarySheet = false }
                )
            }
        }
    }
}
