package sg.edu.nus.iss.canmakan.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.ui.LoginRoute
import sg.edu.nus.iss.canmakan.features.auth.ui.RegistrationRoute

private const val ROUTE_LOGIN = "login"
private const val ROUTE_REGISTRATION = "registration"
private const val ROUTE_INVITE = "invite/{token}"

/** Owns the complete unauthenticated back stack. It is removed when root auth state changes. */
@Composable
fun AuthNavGraph(
    onLoginSuccess: (AuthenticatedUser) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = ROUTE_LOGIN) {
        composable(ROUTE_LOGIN) {
            LoginRoute(
                onLoginSuccess = onLoginSuccess,
                onCreateAccount = {
                    navController.navigate(ROUTE_REGISTRATION) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(ROUTE_REGISTRATION) {
            RegistrationRoute(
                onRegistrationComplete = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_LOGIN) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = ROUTE_INVITE,
            arguments = listOf(navArgument("token") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://canmakan.local/invite/{token}" },
                navDeepLink { uriPattern = "canmakan://invite/{token}" },
            ),
        ) { entry ->
            val token = entry.arguments?.getString("token")
            RegistrationRoute(
                invitationToken = token,
                onRegistrationComplete = {
                    navController.navigate(ROUTE_LOGIN) {
                        popUpTo(ROUTE_LOGIN) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
