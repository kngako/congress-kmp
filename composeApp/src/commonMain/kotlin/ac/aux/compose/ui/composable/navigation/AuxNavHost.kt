package ac.aux.compose.ui.composable.navigation

import ac.aux.compose.AuxGlobal
import ac.aux.compose.database.repository.DatabaseNostrRepository
import ac.aux.compose.managers.DatabaseManager
import ac.aux.compose.ui.composable.CreateProfileScreen
import ac.aux.compose.ui.composable.FeedScreen
import ac.aux.compose.ui.composable.ImplementationPendingScreen
import ac.aux.compose.ui.composable.LandingScreen
import ac.aux.compose.ui.composable.LoadingScreen
import ac.aux.compose.ui.composable.SocialPreconditionScreen
import ac.aux.compose.ui.composable.UnannouncedProfileScreen
import ac.aux.compose.ui.composable.UnsignedProfileScreen
import ac.aux.compose.ui.composable.navigation.routes.BlankRoute
import ac.aux.compose.ui.composable.navigation.routes.CreateProfileRoute
import ac.aux.compose.ui.composable.navigation.routes.FeedRoute
import ac.aux.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import ac.aux.compose.ui.composable.navigation.routes.LandingRoute
import ac.aux.compose.ui.composable.navigation.routes.LoadingRoute
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ac.aux.compose.ui.composable.navigation.routes.SocialPreconditionRoute
import ac.aux.compose.ui.composable.navigation.routes.UnannouncedProfileRoute
import ac.aux.compose.ui.composable.navigation.routes.UnsignedProfileRoute
import ac.aux.compose.ui.composable.widgets.feed.DUMMY_EVENTS
import ac.aux.compose.ui.view.model.NavigationViewModel
import ac.aux.compose.ui.view.state.FeedListUIState
import ac.aux.compose.ui.view.state.NavigationUIState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AuxNavHost(
    auxGlobal: AuxGlobal,
    navController: NavHostController
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val databaseManager = DatabaseManager(auxGlobal)

    val nostrRepository = DatabaseNostrRepository(
        database = databaseManager.auxDatabase
    )

    val navigationViewModel: NavigationViewModel =  viewModel (
        factory = NavigationViewModel.factory(
            NavigationUIState.Loading,
            nostrRepository
        )
    )

    LaunchedEffect(lifecycleOwner) {
        navigationViewModel.navigationUIState.collect { state ->
            when (state) {
                is NavigationUIState.Error -> {
                    navController.navigate(
                        route = ImplementationPendingRoute("Error screen")
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.Loading -> {
                    navController.navigate(
                        route = LoadingRoute
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.Landing -> {
                    navController.navigate(
                        route = LandingRoute
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.UnsignedProfile -> {
                    navController.navigate(
                        route = UnsignedProfileRoute
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.UnannouncedProfile -> {
                    navController.navigate(
                        route = UnannouncedProfileRoute(
                            state.nostrEvent.id
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.ProfileLoaded -> {
                    navController.navigate(
                        route = SocialPreconditionRoute
                    ) {
                        popUpTo(0)
                    }
                }
                else -> {
                    navController.navigate(
                        route = ImplementationPendingRoute("Unsupported $state")
                    ) {
                        popUpTo(0)
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = LoadingRoute
    ) {
        composable<LoadingRoute> {
            LoadingScreen()
        }
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
                        route = CreateProfileRoute()
                    )
                }
            )
        }
        composable<CreateProfileRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<CreateProfileRoute>()

            CreateProfileScreen(
                onNavigateToSocialPreconditionRoute = {
                    navController.navigate(
                        route = SocialPreconditionRoute
                    )
                },
                onNavigateToEndThis = {
                    navController.navigate(
                        route = BlankRoute
                    ) {
                        popUpTo(0)
                    }
                },
                nostrRepository = nostrRepository
            )
        }
        composable<UnsignedProfileRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<UnsignedProfileRoute>()

            UnsignedProfileScreen(
                unsignedNostrEvent = route.toPlaceholderUnsignedNostrEvent()
            )
        }
        composable<UnannouncedProfileRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<UnannouncedProfileRoute>()

            UnannouncedProfileScreen(
                nostrEvent = route.toPlaceholderNostrEvent()
            )
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