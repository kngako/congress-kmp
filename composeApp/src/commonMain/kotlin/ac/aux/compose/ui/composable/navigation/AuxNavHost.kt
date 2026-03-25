package ac.aux.compose.ui.composable.navigation

import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.composable.CreateProfileScreen
import ac.aux.compose.ui.composable.FeedScreen
import ac.aux.compose.ui.composable.ImplementationPendingScreen
import ac.aux.compose.ui.composable.LandingScreen
import ac.aux.compose.ui.composable.SocialPreconditionScreen
import ac.aux.compose.ui.composable.navigation.routes.BlankRoute
import ac.aux.compose.ui.composable.navigation.routes.CreateProfileRoute
import ac.aux.compose.ui.composable.navigation.routes.FeedRoute
import ac.aux.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import ac.aux.compose.ui.composable.navigation.routes.LandingRoute
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ac.aux.compose.ui.composable.navigation.routes.Route
import ac.aux.compose.ui.composable.navigation.routes.SocialPreconditionRoute
import ac.aux.compose.ui.composable.widgets.feed.DUMMY_EVENTS
import ac.aux.compose.ui.view.state.FeedListUIState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

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
                onNavigateToSocialPreconditionRoute = {
                    navController.navigate(
                        route = SocialPreconditionRoute
                    )
                },
                onEndThis = {
                    // TODO: Delete everything and close the app
                    navController.navigate(
                        route = BlankRoute
                    ) {
                        popUpTo(0)
                    }
                },
                nostrRepository = object : NostrRepository {
                    override suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent? {
                        delay(2_100)
                        return unsignedNostrEvent
                    }
                })
        }
        composable<SocialPreconditionRoute> {
            SocialPreconditionScreen(
                onNavigateToSkipForNow = {
                    navController.navigate(
                        route = FeedRoute
                    )
                },
                onNavigateToInviteFriend = {
                    navController.navigate(
                        route = ImplementationPendingRoute("Invite Friend")
                    )
                },
                onNavigateToViewInvites = {
                    navController.navigate(
                        route = ImplementationPendingRoute("View invites")
                    )
                }
            )
        }
        composable<FeedRoute> {
            FeedScreen(
                initialFeedListUIState = FeedListUIState.Loaded(
                    events = DUMMY_EVENTS,
                ),
                onNavigateToEvent = { event ->
                    navController.navigate(
                        route = ImplementationPendingRoute(
                            event.content
                        )
                    )
                }
            )
        }
        composable<BlankRoute> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {


            }
        }
        composable<ImplementationPendingRoute> { backStackEntry ->
            val route: ImplementationPendingRoute = backStackEntry.toRoute()
            ImplementationPendingScreen(
                route.name
            )
        }
    }

}