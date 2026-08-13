package sg.edu.nus.iss.canmakan.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import sg.edu.nus.iss.canmakan.features.auth.data.AuthenticatedUser
import sg.edu.nus.iss.canmakan.features.auth.ui.InviteLandingScreen
import sg.edu.nus.iss.canmakan.features.auth.ui.LoginRoute
import sg.edu.nus.iss.canmakan.features.auth.ui.RegistrationRoute

private const val ROUTE_LOGIN = "login"
private const val ROUTE_LOGIN_WITH_CONTEXT =
    "login?invitationToken={invitationToken}&email={email}"
private const val ROUTE_REGISTRATION = "registration"
private const val ROUTE_REGISTRATION_WITH_TOKEN = "registration?invitationToken={invitationToken}"
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
        composable(
            route = ROUTE_LOGIN_WITH_CONTEXT,
            arguments = listOf(
                navArgument("invitationToken") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("email") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val token = entry.arguments?.getString("invitationToken")
            val email = entry.arguments?.getString("email")
            LoginRoute(
                invitationToken = token,
                prefillEmail = email,
                onLoginSuccess = onLoginSuccess,
                onCreateAccount = {
                    val registerRoute = if (token.isNullOrBlank()) {
                        ROUTE_REGISTRATION
                    } else {
                        "registration?invitationToken=$token"
                    }
                    navController.navigate(registerRoute) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(ROUTE_REGISTRATION) {
            RegistrationRoute(
                onRegistrationAuthenticated = onLoginSuccess,
                onLoginRequired = { email ->
                    navController.navigate(loginRoute(email = email)) {
                        launchSingleTop = true
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = ROUTE_REGISTRATION_WITH_TOKEN,
            arguments = listOf(
                navArgument("invitationToken") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val token = entry.arguments?.getString("invitationToken")
            RegistrationRoute(
                invitationToken = token,
                onRegistrationAuthenticated = onLoginSuccess,
                onLoginRequired = { email ->
                    navController.navigate(loginRoute(token, email)) {
                        launchSingleTop = true
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = ROUTE_INVITE,
            arguments = listOf(navArgument("token") { type = NavType.StringType }),
            deepLinks = InviteWebDeepLinks.uriPatterns().map { pattern ->
                navDeepLink { uriPattern = pattern }
            },
        ) { entry ->
            val token = entry.arguments?.getString("token").orEmpty()
            InviteLandingScreen(
                onCreateAccount = {
                    navController.navigate("registration?invitationToken=$token") {
                        launchSingleTop = true
                    }
                },
                onSignIn = {
                    navController.navigate("login?invitationToken=$token") {
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

private fun loginRoute(invitationToken: String? = null, email: String? = null): String {
    val parameters = buildList {
        invitationToken?.takeIf { it.isNotBlank() }?.let {
            add("invitationToken=${Uri.encode(it)}")
        }
        email?.takeIf { it.isNotBlank() }?.let { add("email=${Uri.encode(it)}") }
    }
    return if (parameters.isEmpty()) ROUTE_LOGIN else "$ROUTE_LOGIN?${parameters.joinToString("&")}"
}
