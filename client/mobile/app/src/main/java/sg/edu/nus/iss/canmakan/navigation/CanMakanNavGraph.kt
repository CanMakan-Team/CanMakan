package sg.edu.nus.iss.canmakan

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.DietaryRestrictionSheet
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.family.ProfileDrawerContent
import sg.edu.nus.iss.canmakan.features.product.history.HistoryScreen
import sg.edu.nus.iss.canmakan.features.product.model.ProductSampleData
import sg.edu.nus.iss.canmakan.features.product.scan.ScannerScreen
import sg.edu.nus.iss.canmakan.features.product.verdict.ProductDetailScreen
import sg.edu.nus.iss.canmakan.navigation.CanMakanNavGraphViewModel

private const val ROUTE_SCANNER = "scanner"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_PRODUCT_DETAIL = "product_detail"

// The top-level screen. It wires together the navigation between the
// three screens, the side drawer, and the edit dietary requirements sheet.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanMakanNavGraph(
    navGraphViewModel: CanMakanNavGraphViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val currentProfileId = navGraphViewModel.currentProfileId.collectAsStateWithLifecycle()

    
    var activeProfile = ProductSampleData.profiles.firstOrNull {it.id == currentProfileId.value}?: ProductSampleData.profiles.first()
    var showEditDietarySheet by remember { mutableStateOf(false) }

    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawer() = scope.launch { drawerState.close() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ProfileDrawerContent(
                    profiles = ProductSampleData.profiles,
                    activeProfile = activeProfile,
                    onProfileSelected = { selected ->
                        navGraphViewModel.switchProfile(selected.id)
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
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) }
                )
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    activeProfile = activeProfile,
                    entries = ProductSampleData.scanHistory,
                    onMenuClick = { openDrawer() },
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { },
                    onEntryClick = { navController.navigate(ROUTE_PRODUCT_DETAIL) }
                )
            }
            composable(ROUTE_PRODUCT_DETAIL) {
                ProductDetailScreen(
                    product = ProductSampleData.scannedProduct,
                    flags = ProductSampleData.productFlags,
                    alternatives = ProductSampleData.alternatives,
                    profileName = activeProfile.name,
                    onBackClick = { navController.popBackStack() },
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) }
                )
            }
        }

        if (showEditDietarySheet) {
            ModalBottomSheet(onDismissRequest = { showEditDietarySheet = false }) {
                DietaryRestrictionSheet(
                    profileName = activeProfile.name,
                    profileRole = activeProfile.role,
                    onCancel = { showEditDietarySheet = false },
                    onSave = {showEditDietarySheet = false}
                )
            }
        }
    }
}
