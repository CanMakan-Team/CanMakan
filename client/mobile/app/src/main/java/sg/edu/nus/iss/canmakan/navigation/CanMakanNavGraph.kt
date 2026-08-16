package sg.edu.nus.iss.canmakan.navigation

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.RestrictionEditAuthorization
import sg.edu.nus.iss.canmakan.features.dietaryprofile.restrictions.ui.DietaryRestrictionSheet
import sg.edu.nus.iss.canmakan.features.family.ProfileDrawerContent
import sg.edu.nus.iss.canmakan.features.family.ProfileRelationshipDisplay
import sg.edu.nus.iss.canmakan.features.family.ActiveProfileManager
import sg.edu.nus.iss.canmakan.features.product.history.ScanHistoryViewModel
import sg.edu.nus.iss.canmakan.features.product.history.ui.HistoryScreen
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail
import sg.edu.nus.iss.canmakan.features.product.scan.ScannerScreen
import sg.edu.nus.iss.canmakan.features.product.verdict.ProductDetailScreen
import sg.edu.nus.iss.canmakan.features.family.ui.CreateDependantProfileScreen
import sg.edu.nus.iss.canmakan.features.family.ui.CreateFamilyCircleScreen
import sg.edu.nus.iss.canmakan.features.family.ui.FamilyRestrictionSummaryScreen
import sg.edu.nus.iss.canmakan.features.family.ui.FamilyRestrictionSummaryViewModel
import sg.edu.nus.iss.canmakan.features.family.ui.InviteFamilyMemberScreen
import sg.edu.nus.iss.canmakan.features.family.ui.ManageFamilyScreen
import sg.edu.nus.iss.canmakan.features.notifications.NotificationsInboxScreen
import sg.edu.nus.iss.canmakan.features.settings.SettingsScreen
import sg.edu.nus.iss.canmakan.features.settings.SettingsViewModel

private const val ROUTE_SCANNER = "scanner"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_PRODUCT_DETAIL = "product_detail"
private const val ROUTE_CREATE_FAMILY = "create_family"
private const val ROUTE_MANAGE_FAMILY = "family/manage"
private const val ROUTE_INVITE_MEMBER = "family/invite"
private const val ROUTE_DEPENDANT_PROFILE = "family/dependant"
private const val ROUTE_NOTIFICATIONS = "notifications"
private const val ROUTE_SETTINGS = "settings"

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
    invitationClaimError: String? = null,
    onRetryInvitationClaim: () -> Unit = {},
    onRequestSelfProfileSetup: () -> Unit = {},
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val editDietarySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val currentProfileId by navGraphViewModel.currentProfileId.collectAsStateWithLifecycle()
    val activeRestrictions by navGraphViewModel.activeRestrictions.collectAsStateWithLifecycle()
    val profiles by navGraphViewModel.profiles.collectAsStateWithLifecycle()
    val hasFamily by navGraphViewModel.hasFamily.collectAsStateWithLifecycle()
    val familyName by navGraphViewModel.familyName.collectAsStateWithLifecycle()
    val showManageFamilyActions by navGraphViewModel.showManageFamilyActions.collectAsStateWithLifecycle()
    val selfProfileId by navGraphViewModel.selfProfileId.collectAsStateWithLifecycle()
    val memberRole by navGraphViewModel.memberRole.collectAsStateWithLifecycle()
    val hasUserSession by navGraphViewModel.hasUserSession.collectAsStateWithLifecycle()
    val isLoading by navGraphViewModel.isLoading.collectAsStateWithLifecycle()
    val error by navGraphViewModel.error.collectAsStateWithLifecycle()
    val pendingVerdict by navGraphViewModel.pendingVerdict.collectAsStateWithLifecycle()
    val isCreatingFamily by navGraphViewModel.isCreatingFamily.collectAsStateWithLifecycle()
    val createFamilyError by navGraphViewModel.createFamilyError.collectAsStateWithLifecycle()
    val switchProfileError by navGraphViewModel.switchProfileError.collectAsStateWithLifecycle()
    val isSwitchingProfile by navGraphViewModel.isSwitchingProfile.collectAsStateWithLifecycle()
    val hasUnreadNotifications by navGraphViewModel.hasUnreadNotifications.collectAsStateWithLifecycle()
    val notificationsEnabled by navGraphViewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val notificationsEnabledError by navGraphViewModel.notificationsEnabledError.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Re-entering after a retried invitation claim may reuse this activity-scoped ViewModel.
    // Refresh so the newly joined family/profile is visible without restarting the app.
    LaunchedEffect(Unit) {
        navGraphViewModel.refreshRestrictions()
    }

    val activeProfile = currentProfileId
        .takeIf { it > ActiveProfileManager.UNSET_PROFILE_ID }
        ?.let { profileId -> profiles.firstOrNull { it.id == profileId } }

    val editDietaryButtonLabel = remember(activeProfile?.id, hasFamily, selfProfileId, memberRole) {
        val profileId = activeProfile?.id
        if (profileId == null) {
            RestrictionEditAuthorization.EDIT_DIETARY_PROFILE_LABEL
        } else {
            RestrictionEditAuthorization.dietaryProfileButtonLabel(
                RestrictionEditAuthorization.mayEditRestrictions(
                    profileId = profileId,
                    hasFamily = hasFamily,
                    selfProfileId = selfProfileId,
                    memberRole = memberRole,
                )
            )
        }
    }

    // Account/profile context is loaded before exposing profile-dependent actions. Once loading
    // completes, a missing profile is a valid shell state rather than a navigation gate.
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var showEditDietarySheet by remember { mutableStateOf(false) }

    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawer() = scope.launch { drawerState.close() }

    /** Return to Scanner as the shell home, clearing overlays like Notifications. */
    fun navigateToScannerHome() {
        navController.navigate(ROUTE_SCANNER) {
            launchSingleTop = true
            popUpTo(ROUTE_SCANNER) { inclusive = false }
        }
    }

    fun closeEditDietarySheet(refresh: Boolean = false) {
        showEditDietarySheet = false
        if (refresh) {
            navGraphViewModel.refreshRestrictions()
            // Product detail still shows a verdict computed against the old restrictions.
            if (currentRoute == ROUTE_PRODUCT_DETAIL) {
                if (!navController.popBackStack()) {
                    navigateToScannerHome()
                }
            }
        }
    }

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
                    selfProfileId = selfProfileId,
                    memberRole = memberRole,
                    isSwitchingProfile = isSwitchingProfile,
                    onProfileSelected = { selected ->
                        navGraphViewModel.switchProfile(selected.id)
                    },
                    onEditDietaryClick = {
                        closeDrawer()
                        if (activeProfile == null) {
                            onRequestSelfProfileSetup()
                        } else {
                            showEditDietarySheet = true
                        }
                    },
                    editDietaryButtonLabel = editDietaryButtonLabel,
                    onScannerClick = {
                        closeDrawer()
                        navigateToScannerHome()
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
                    onManageFamilyClick = {
                        closeDrawer()
                        navController.navigate(ROUTE_MANAGE_FAMILY)
                    },
                    onSettingsClick = {
                        closeDrawer()
                        navController.navigate(ROUTE_SETTINGS)
                    },
                )
            }
        }
    ) {
        fun openNotifications() {
            navController.navigate(ROUTE_NOTIFICATIONS) {
                launchSingleTop = true
            }
        }

        fun openActiveProfileDietary() {
            if (activeProfile == null) {
                onRequestSelfProfileSetup()
            } else {
                showEditDietarySheet = true
            }
        }

        Column {
            invitationClaimError?.let { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetryInvitationClaim) {
                        Text("Retry invitation")
                    }
                }
            }
            error?.let { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = navGraphViewModel::refreshRestrictions) {
                        Text("Retry profile loading")
                    }
                }
            }
            switchProfileError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { navGraphViewModel.clearSwitchProfileError() },
                )
            }
            // NavHost is used to switch between the three screens
            NavHost(
                navController = navController,
                startDestination = ROUTE_SCANNER,
                modifier = Modifier.weight(1f),
            ) {
            composable(ROUTE_SCANNER) {
                ScannerScreen(
                    activeProfile = activeProfile,
                    activeRestrictions = activeRestrictions,

                    // Open the drawer when the menu button is clicked
                    onMenuClick = { openDrawer() },
                    onNotificationsClick = { openNotifications() },
                    hasUnreadNotifications = hasUnreadNotifications,

                    // Navigate to the history screen when the history button is clicked
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },

                    // Navigate to the history screen when the history button is clicked
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
                    onSetUpProfile = onRequestSelfProfileSetup,
                    onActiveProfileClick = { openActiveProfileDietary() },

                    // Navigate to the product detail screen when a verdict is ready
                    onVerdictReady = { detail ->
                        activeProfile?.id?.let { profileId ->
                            navGraphViewModel.setPendingVerdict(profileId, detail)
                            navController.navigate(ROUTE_PRODUCT_DETAIL)
                        }
                    },
                )
            }
            /**
             * (UC6) Navigate to the Family Allergy Matrix Screen
             */
            composable("family/restrictions") {
                // 1. Instantiate the ViewModel at the NavGraph level
                val viewModel: FamilyRestrictionSummaryViewModel = hiltViewModel()

                // 2. Collect the state safely
                val uiState by viewModel.uiState.collectAsState()

                // 3. Pass the stateless UI state and navigation callbacks down
                LaunchedEffect(Unit) {
                    viewModel.fetchSummary()
                }
                FamilyRestrictionSummaryScreen(
                    uiState = uiState,
                    profiles = profiles,
                    selfProfileId = selfProfileId,
                    memberRole = memberRole,
                    onMenuClick = { openDrawer() },
                    onNotificationsClick = { openNotifications() },
                    hasUnreadNotifications = hasUnreadNotifications,
                    onNavigateToEditMembers = {
                        navController.navigate(ROUTE_MANAGE_FAMILY)
                    },
                )
            }
            composable(ROUTE_HISTORY) {
                val scanHistoryViewModel: ScanHistoryViewModel = hiltViewModel()
                val scanHistoryUiState by scanHistoryViewModel.scanHistoryUiState.collectAsStateWithLifecycle()

                HistoryScreen(
                    activeProfile = activeProfile,
                    entries = scanHistoryUiState.scanHistory,
                    isLoading = scanHistoryUiState.isLoading,
                    requiresProfileSetup = scanHistoryUiState.requiresProfileSetup,
                    errorMessage = scanHistoryUiState.errorMessage,
                    onMenuClick = { openDrawer() },
                    onNotificationsClick = { openNotifications() },
                    hasUnreadNotifications = hasUnreadNotifications,
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { },
                    onSetUpProfile = onRequestSelfProfileSetup,
                    onActiveProfileClick = { openActiveProfileDietary() },
                    onEntryClick = { entry ->
                        val alternatives = scanHistoryUiState.alternativesByScanId[entry.id].orEmpty()
                        navGraphViewModel.setPendingVerdict(
                            profileId = entry.profileId,
                            detail = VerdictDetail.fromHistoryEntry(entry, alternatives),
                        )
                        navController.navigate(ROUTE_PRODUCT_DETAIL)
                    }
                )
            }
            composable(ROUTE_PRODUCT_DETAIL) {
                val detail = pendingVerdict
                // If there is no pending verdict, navigate back to the scanner screen
                if (detail == null || activeProfile == null) {
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
                        alternativesError = detail.alternativesError,
                        onBackClick = { navController.popBackStack() },
                        onScanClick = { navController.navigate(ROUTE_SCANNER) },
                        onHistoryClick = { navController.navigate(ROUTE_HISTORY) }
                    )
                }
            }
            composable(ROUTE_CREATE_FAMILY) {
                if (hasFamily) {
                    LaunchedEffect(Unit) {
                        navController.navigate(ROUTE_MANAGE_FAMILY) {
                            popUpTo(ROUTE_CREATE_FAMILY) { inclusive = true }
                        }
                    }
                } else {
                    CreateFamilyCircleScreen(
                        isSubmitting = isCreatingFamily,
                        errorMessage = createFamilyError,
                        onMenuClick = { openDrawer() },
                        onNotificationsClick = { openNotifications() },
                        hasUnreadNotifications = hasUnreadNotifications,
                        onScanClick = { navController.navigate(ROUTE_SCANNER) },
                        onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
                        onBackClick = { navController.popBackStack() },
                        onCreateClick = { name ->
                            navGraphViewModel.createFamilyCircle(name) {
                                Toast.makeText(
                                    context,
                                    "Success! You can now add someone to your family circle",
                                    Toast.LENGTH_LONG,
                                ).show()
                                navController.navigate(ROUTE_MANAGE_FAMILY) {
                                    popUpTo(ROUTE_CREATE_FAMILY) { inclusive = true }
                                }
                            }
                        },
                    )
                }
            }
            composable(ROUTE_MANAGE_FAMILY) {
                ManageFamilyScreen(
                    familyName = familyName,
                    onMenuClick = { openDrawer() },
                    onNotificationsClick = { openNotifications() },
                    hasUnreadNotifications = hasUnreadNotifications,
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
                    onBackClick = { navController.popBackStack() },
                    onInviteClick = { navController.navigate(ROUTE_INVITE_MEMBER) },
                    onDependantClick = { navController.navigate(ROUTE_DEPENDANT_PROFILE) },
                )
            }
            composable(ROUTE_DEPENDANT_PROFILE) {
                CreateDependantProfileScreen(
                    onMenuClick = { openDrawer() },
                    onNotificationsClick = { openNotifications() },
                    hasUnreadNotifications = hasUnreadNotifications,
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
                    onBackClick = { navController.popBackStack() },
                    onCancelClick = { navController.popBackStack() },
                    onCreated = {
                        navController.popBackStack()
                        navGraphViewModel.refreshRestrictions()
                    },
                )
            }
            composable(ROUTE_INVITE_MEMBER) {
                InviteFamilyMemberScreen(
                    onMenuClick = { openDrawer() },
                    onNotificationsClick = { openNotifications() },
                    hasUnreadNotifications = hasUnreadNotifications,
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
                    onBackClick = { navController.popBackStack() },
                    onCancelClick = { navController.popBackStack() },
                    onInviteCreated = {
                        navController.popBackStack()
                        navGraphViewModel.refreshRestrictions()
                    },
                )
            }
            composable(ROUTE_NOTIFICATIONS) {
                // Opening this screen marks every card read on the backend. Refresh the bell
                // badge when the screen closes so it clears wherever AppTopBar is shown next.
                DisposableEffect(Unit) {
                    onDispose { navGraphViewModel.refreshNotifications() }
                }
                NotificationsInboxScreen(
                    onMenuClick = { openDrawer() },
                    onNotificationsClick = { openNotifications() },
                    hasUnreadNotifications = hasUnreadNotifications,
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
                    onBackClick = { navController.popBackStack() },
                    onAccepted = {
                        navGraphViewModel.refreshRestrictions()
                        navController.popBackStack()
                    },
                    onMarkedAllRead = { navGraphViewModel.refreshNotifications() },
                )
            }
            composable(ROUTE_SETTINGS) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                val isDeletingAccount by settingsViewModel.isDeletingAccount.collectAsStateWithLifecycle()
                val deleteAccountError by settingsViewModel.deleteAccountError.collectAsStateWithLifecycle()
                SettingsScreen(
                    onMenuClick = { openDrawer() },
                    onNotificationsClick = { openNotifications() },
                    hasUnreadNotifications = hasUnreadNotifications,
                    onBackClick = { navController.popBackStack() },
                    onScanClick = { navController.navigate(ROUTE_SCANNER) },
                    onHistoryClick = { navController.navigate(ROUTE_HISTORY) },
                    notificationsEnabled = notificationsEnabled,
                    onNotificationsEnabledChanged = navGraphViewModel::setNotificationsEnabled,
                    notificationsEnabledError = notificationsEnabledError,
                    isDeletingAccount = isDeletingAccount,
                    deleteAccountError = deleteAccountError,
                    onConfirmDeleteAccount = {
                        settingsViewModel.deleteOwnAccount(onSuccess = onSignOut)
                    },
                )
            }
        }
        }

        // ModalBottomSheet is used to open and close the edit dietary requirements sheet
        if (showEditDietarySheet && activeProfile != null) {
            ModalBottomSheet(
                onDismissRequest = { closeEditDietarySheet() },
                sheetState = editDietarySheetState) {
                DietaryRestrictionSheet(
                    profileName = activeProfile.profileName,
                    profileRole = ProfileRelationshipDisplay.sheetRoleLine(
                        ProfileRelationshipDisplay.tags(
                            profileId = activeProfile.id,
                            relationship = activeProfile.relationship,
                            isFamilyAdminProfile = activeProfile.isPrimary,
                            viewerSelfProfileId = selfProfileId,
                            viewerMemberRole = memberRole,
                        ),
                    ),
                    onCancel = { closeEditDietarySheet() },
                    onSave = { closeEditDietarySheet(refresh = true) },
                )
            }
        }
    }
}
