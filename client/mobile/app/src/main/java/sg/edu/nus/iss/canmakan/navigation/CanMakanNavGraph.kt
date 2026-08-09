package sg.edu.nus.iss.canmakan.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.ui.DietaryRestrictionSheet
import sg.edu.nus.iss.canmakan.features.family.ProfileDrawerContent
import sg.edu.nus.iss.canmakan.features.product.history.ScanHistoryViewModel
import sg.edu.nus.iss.canmakan.features.product.history.ui.HistoryScreen
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
import sg.edu.nus.iss.canmakan.features.product.scan.ScannerScreen
import sg.edu.nus.iss.canmakan.features.product.verdict.ProductDetailScreen
import sg.edu.nus.iss.canmakan.features.family.ui.AddProfileToFamilyScreen
import sg.edu.nus.iss.canmakan.features.family.ui.CreateFamilyCircleScreen
import sg.edu.nus.iss.canmakan.features.family.ui.CreateNewProfileScreen
import sg.edu.nus.iss.canmakan.features.family.ui.FamilyRestrictionSummaryScreen
import sg.edu.nus.iss.canmakan.features.family.ui.FamilyRestrictionSummaryViewModel

private const val ROUTE_SCANNER = "scanner"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_PRODUCT_DETAIL = "product_detail"
private const val ROUTE_CREATE_FAMILY = "create_family"
private const val ROUTE_CREATE_NEW = "create_new"
private const val ROUTE_ADD_PROFILE = "add_profile"

/* The top-level screen. It wires together the navigation between the
 * three screens, the side drawer, and the edit dietary requirements sheet.
 *
 * author Amelia; Kwok Heng; Khai
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanMakanNavGraph(
    navGraphViewModel: CanMakanNavGraphViewModel = hiltViewModel(),
    onSignOut: () -> Unit = {},
) {
    val navController = rememberNavController()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val editDietarySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val currentProfileId by navGraphViewModel.currentProfileId.collectAsStateWithLifecycle()
    val activeRestrictions by navGraphViewModel.activeRestrictions.collectAsStateWithLifecycle()
    val profiles by navGraphViewModel.profiles.collectAsStateWithLifecycle()
    val hasFamily by navGraphViewModel.hasFamily.collectAsStateWithLifecycle()
    val showManageFamilyActions by navGraphViewModel.showManageFamilyActions.collectAsStateWithLifecycle()
    val hasUserSession by navGraphViewModel.hasUserSession.collectAsStateWithLifecycle()
    val isLoading by navGraphViewModel.isLoading.collectAsStateWithLifecycle()
    val error by navGraphViewModel.error.collectAsStateWithLifecycle()
    val pendingVerdict by navGraphViewModel.pendingVerdict.collectAsStateWithLifecycle()
    val isCreatingFamily by navGraphViewModel.isCreatingFamily.collectAsStateWithLifecycle()
    val createFamilyError by navGraphViewModel.createFamilyError.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val activeProfile = profiles.firstOrNull { it.id == currentProfileId }
        ?: profiles.firstOrNull()

    // If activeProfile is null, show a loading screen while profiles are being fetched
    if (activeProfile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (error != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    Button(onClick = { navGraphViewModel.refreshRestrictions() }) {
                        Text("Retry")
                    }
                }
            }
        }
        return
    }

    var showEditDietarySheet by remember { mutableStateOf(false) }

    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawer() = scope.launch { drawerState.close() }

    // ModalNavigationDrawer is used to open and close the drawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ProfileDrawerContent(
                    currentRoute = currentRoute,
                    profiles = profiles,
                    activeProfile = activeProfile,
                    hasFamily = hasFamily,
                    hasUserSession = hasUserSession,
                    noFamilyMessage = when {
                        hasFamily -> null
                        hasUserSession -> CanMakanNavGraphViewModel.NO_FAMILY_MESSAGE
                        else -> CanMakanNavGraphViewModel.NO_SESSION_FAMILY_MESSAGE
                    },
                    showManageFamilyActions = showManageFamilyActions,
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
                    onFamilyAllergySummaryClick = {
                        closeDrawer()
                        navController.navigate("family/restrictions")
                    },
                    onHistoryClick = {
                        closeDrawer()
                        navController.navigate(ROUTE_HISTORY)
                    },
                    onSignOutClick = {
                        closeDrawer()
                        onSignOut()
                    },
                    onCloseClick = { closeDrawer() },
                    onCreateFamilyCircleClick = {
                        closeDrawer()
                        navGraphViewModel.clearCreateFamilyError()
                        navController.navigate(ROUTE_CREATE_FAMILY)
                    },
                    onCreateNewClick = {
                        closeDrawer()
                        navController.navigate(ROUTE_CREATE_NEW)
                    },
                    onAddProfileClick = {
                        closeDrawer()
                        navController.navigate(ROUTE_ADD_PROFILE)
                    },
                )
            }
        }
    ) {
        // NavHost is used to switch between the three screens
        NavHost(navController = navController, startDestination = ROUTE_SCANNER) {
            composable(ROUTE_SCANNER) {
                ScannerScreen(
                    activeProfile = activeProfile,
                    activeRestrictions = activeRestrictions,

                    // Open the drawer when the menu button is clicked
                    onMenuClick = { openDrawer() },

                    // Navigate to the history screen when the history button is clicked
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },

                    // Navigate to the history screen when the history button is clicked
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) },

                    // Navigate to the product detail screen when a verdict is ready
                    onVerdictReady = { detail ->
                        navGraphViewModel.setPendingVerdict(detail)
                        navController.navigate(ROUTE_PRODUCT_DETAIL)
                    }
                )
            }
            /**
             * (UC6) Navigate to the Family Allergy Matrix Screen
             */
            composable("family/restrictions") {
                val viewModel: FamilyRestrictionSummaryViewModel = hiltViewModel()

                FamilyRestrictionSummaryScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    // Member-edit destination is web-primary; keep empty-state CTA local until UC9/UC12.
                    onNavigateToEditMembers = { navController.popBackStack() }
                )
            }
            composable(ROUTE_HISTORY) {
                val scanHistoryViewModel: ScanHistoryViewModel = hiltViewModel()
                val scanHistoryUiState by scanHistoryViewModel.scanHistoryUiState.collectAsStateWithLifecycle()

                HistoryScreen(
                    activeProfile = activeProfile,
                    entries = scanHistoryUiState.scanHistory,
                    isLoading = scanHistoryUiState.isLoading,
                    errorMessage = scanHistoryUiState.errorMessage,
                    onMenuClick = { openDrawer() },
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { },
                    onEntryClick = { entry ->
                        navGraphViewModel.setPendingVerdict(VerdictDetail.fromHistoryEntry(entry))
                        navController.navigate(ROUTE_PRODUCT_DETAIL)
                    }
                )
            }
            composable(ROUTE_PRODUCT_DETAIL) {
                val detail = pendingVerdict
                // If there is no pending verdict, navigate back to the scanner screen
                if (detail == null) {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }

                // Otherwise, show the product detail screen with the pending verdict
                } else {
                    ProductDetailScreen(
                        product = detail.product,
                        verdict = detail.verdict,
                        flags = detail.flags,
                        alternatives = detail.alternatives,
                        profileName = activeProfile.profileName,
                        explanation = detail.explanation,
                        onBackClick = { navController.popBackStack() },
                        onScanClick = { navController.navigate(ROUTE_SCANNER) },
                        onHistoryClick = { navController.navigate(ROUTE_HISTORY) }
                    )
                }
            }
            composable(ROUTE_CREATE_FAMILY) {
                if (hasFamily) {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                } else {
                    CreateFamilyCircleScreen(
                        activeProfile = activeProfile,
                        isSubmitting = isCreatingFamily,
                        errorMessage = createFamilyError,
                        onMenuClick = { openDrawer() },
                        onScanClick = { navController.navigate(ROUTE_SCANNER) },
                        onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
                        onBackClick = { navController.popBackStack() },
                        onCreateClick = { name ->
                            navGraphViewModel.createFamilyCircle(name) {
                                navController.popBackStack()
                            }
                        },
                    )
                }
            }
            composable(ROUTE_CREATE_NEW) {
                CreateNewProfileScreen(
                    activeProfile = activeProfile,
                    onMenuClick = { openDrawer() },
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
                    onBackClick = { navController.popBackStack() },
                    onCancelClick = { navController.popBackStack() },
                    onCreateClick = { _, _, _ -> 
                        navController.popBackStack()
                        navGraphViewModel.refreshRestrictions()
                    }
                )
            }
            composable(ROUTE_ADD_PROFILE) {
                AddProfileToFamilyScreen(
                    activeProfile = activeProfile,
                    onMenuClick = { openDrawer() },
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
                    onBackClick = { navController.popBackStack() },
                    onCancelClick = { navController.popBackStack() },
                    onInviteCreated = {
                        navController.popBackStack()
                        navGraphViewModel.refreshRestrictions()
                    }
                )
            }
        }

        // ModalBottomSheet is used to open and close the edit dietary requirements sheet
        if (showEditDietarySheet) {
            ModalBottomSheet(
                onDismissRequest = { showEditDietarySheet = false },
                sheetState = editDietarySheetState) {
                DietaryRestrictionSheet(
                    profileName = activeProfile.profileName,
                    profileRole = activeProfile.relationship,
                    onCancel = { showEditDietarySheet = false },
                    onSave = {
                        showEditDietarySheet = false
                        navGraphViewModel.refreshRestrictions()
                    }
                )
            }
        }
    }
}
