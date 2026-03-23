package ac.aux.compose.ui.composable.navigation

import ac.aux.compose.ui.composable.CreateProfileScreen
import ac.aux.compose.ui.composable.ImplementationPendingScreen
import ac.aux.compose.ui.composable.LandingScreen
import ac.aux.compose.ui.composable.navigation.routes.CreateProfileRoute
import ac.aux.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import ac.aux.compose.ui.composable.navigation.routes.LandingRoute
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ac.aux.compose.ui.composable.navigation.routes.Route

@Composable
fun AuxNavHost(
    navController: NavHostController,
    startDestination: Route
) {

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<LandingRoute> {
            LandingScreen(
                onNavigateToLearnMore = {
                    navController.navigate(
                        route = ImplementationPendingRoute("Learn more")
                    )
                },
                onNavigateToSignIn = {
                    navController.navigate(
                        route = ImplementationPendingRoute("Sign in")
                    )
                },
                onNavigateToCreateProfile = {
                    navController.navigate(
                        route = CreateProfileRoute
                    )
                }
            )
        }
        composable<CreateProfileRoute> {
            CreateProfileScreen(
                onNavigateToChatListRoute = {
                    navController.navigate(
                        route = ImplementationPendingRoute("Feed")
                    )
                }
            )
        }
        composable<ImplementationPendingRoute> { backStackEntry ->
            val route: ImplementationPendingRoute = backStackEntry.toRoute()
            ImplementationPendingScreen(
                route.name
            )
        }
    }

}