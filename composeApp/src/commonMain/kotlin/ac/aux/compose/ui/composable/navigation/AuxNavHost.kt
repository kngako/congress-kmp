package ac.aux.compose.ui.composable.navigation

import ac.aux.compose.AuxGlobal
import ac.aux.compose.database.repository.DatabaseNostrRepository
import ac.aux.compose.database.repository.DatabaseSearchRepository
import ac.aux.compose.managers.DatabaseManager
import ac.aux.compose.ui.composable.CreateProfileScreen
import ac.aux.compose.ui.composable.FeedScreen
import ac.aux.compose.ui.composable.ImplementationPendingScreen
import ac.aux.compose.ui.composable.LandingScreen
import ac.aux.compose.ui.composable.LoadingScreen
import ac.aux.compose.ui.composable.NostrEventDetailScreen
import ac.aux.compose.ui.composable.SearchResultScreen
import ac.aux.compose.ui.composable.SearchScreen
import ac.aux.compose.ui.composable.SignInToProfileScreen
import ac.aux.compose.ui.composable.SocialPreconditionScreen
import ac.aux.compose.ui.composable.UnannouncedProfileScreen
import ac.aux.compose.ui.composable.UnindexedProfileScreen
import ac.aux.compose.ui.composable.UnqueuedProfileScreen
import ac.aux.compose.ui.composable.UnqueuedProfileSynchronizationScreen
import ac.aux.compose.ui.composable.UnsignedProfileScreen
import ac.aux.compose.ui.composable.UnsyncedProfileScreen
import ac.aux.compose.ui.composable.WriteNewNoteScreen
import ac.aux.compose.ui.composable.navigation.routes.BlankRoute
import ac.aux.compose.ui.composable.navigation.routes.CreateProfileRoute
import ac.aux.compose.ui.composable.navigation.routes.FeedRoute
import ac.aux.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import ac.aux.compose.ui.composable.navigation.routes.LandingRoute
import ac.aux.compose.ui.composable.navigation.routes.LoadingRoute
import ac.aux.compose.ui.composable.navigation.routes.NostrEventDetailRoute
import ac.aux.compose.ui.composable.navigation.routes.SearchResultRoute
import ac.aux.compose.ui.composable.navigation.routes.SearchRoute
import ac.aux.compose.ui.composable.navigation.routes.SignInRoute
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ac.aux.compose.ui.composable.navigation.routes.SocialPreconditionRoute
import ac.aux.compose.ui.composable.navigation.routes.UnannouncedProfileRoute
import ac.aux.compose.ui.composable.navigation.routes.UnindexedProfileRoute
import ac.aux.compose.ui.composable.navigation.routes.UnqueuedProfileRoute
import ac.aux.compose.ui.composable.navigation.routes.UnqueuedProfileSynchronizationRoute
import ac.aux.compose.ui.composable.navigation.routes.UnsignedProfileRoute
import ac.aux.compose.ui.composable.navigation.routes.UnsyncedProfileRoute
import ac.aux.compose.ui.composable.navigation.routes.WriteNewNoteRoute
import ac.aux.compose.ui.view.model.NavigationViewModel
import ac.aux.compose.ui.view.state.FeedListUIState
import ac.aux.compose.ui.view.state.NavigationUIState
import ac.aux.compose.ui.view.state.NostrEventDetailUIState
import ac.aux.compose.ui.view.state.SearchUIState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Composable
fun AuxNavHost(
    auxGlobal: AuxGlobal,
    navController: NavHostController
) {
    val logger = Logger.withTag("AuxNavHost")
    val lifecycleOwner = LocalLifecycleOwner.current

    val databaseManager = DatabaseManager(auxGlobal)

    val databaseNostrRepository = DatabaseNostrRepository(
        database = databaseManager.auxDatabase
    )
    val searchRepository = DatabaseSearchRepository(
        database = databaseManager.auxDatabase
    )

    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            logger.e("Caught exception: ${throwable.message}", throwable)
        }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    val applicationMainScope = CoroutineScope(Dispatchers.Main)


    val navigationViewModel: NavigationViewModel =  viewModel (
        factory = NavigationViewModel.factory(
            initialNavigationUIState = NavigationUIState.Loading,
            nostrRepository = databaseNostrRepository,
            relayRepository = databaseNostrRepository,
            scope = applicationIOScope
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
                        route = UnsignedProfileRoute(
                            state.unsignedNostrEvent.id
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.UnsyncedProfile -> {
                    navController.navigate(
                        route = UnsyncedProfileRoute(
                            state.unsignedNostrEvent.pubKey
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.UnqueuedProfileSynchronization -> {
                    navController.navigate(
                        route = UnqueuedProfileSynchronizationRoute(
                            unsignedNostrEventId = state.unsignedNostrEvent.id,
                            publicKey =  state.unsignedNostrEvent.pubKey
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.UnindexedProfile -> {
                    navController.navigate(
                        route = UnindexedProfileRoute(
                            state.nostrEvent.id
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.UnqueuedProfile -> {
                    navController.navigate(
                        route = UnqueuedProfileRoute(
                            state.profile.nostrEventId
                        )
                    )
                }
                is NavigationUIState.UnannouncedProfile -> {
                    navController.navigate(
                        route = UnannouncedProfileRoute(
                            state.broadcastNostrEventRequest.nostrEventId
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
                        route = SignInRoute
                    )
                },
                onNavigateToCreateProfile = {
                    navController.navigate(
                        route = CreateProfileRoute()
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
                onNavigateToEndThis = {
                    navController.navigate(
                        route = BlankRoute
                    ) {
                        popUpTo(0)
                    }
                },
                nostrRepository = databaseNostrRepository
            )
        }
        composable< WriteNewNoteRoute> {
            WriteNewNoteScreen(
                onNostrEventPublished = {
                    applicationMainScope.launch {
                        navController.popBackStack()
                    }
                },
                nostrRepository = databaseNostrRepository
            )
        }
        composable<SignInRoute> {
            SignInToProfileScreen(
                nostrRepository = databaseNostrRepository
            )
        }
        composable<UnsignedProfileRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<UnsignedProfileRoute>()

            UnsignedProfileScreen()
        }
        composable<UnsyncedProfileRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<UnsyncedProfileRoute>()
            UnsyncedProfileScreen(
                unsyncedProfilePublicKey = route.publicKey
            )
        }
        composable<UnqueuedProfileSynchronizationRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<UnqueuedProfileSynchronizationRoute>()
            UnqueuedProfileSynchronizationScreen(
                unsignedNostrEventId = route.unsignedNostrEventId,
                profilePublicKey = route.publicKey,
                nostrRepository = databaseNostrRepository
            )
        }
        composable<UnindexedProfileRoute> {
            UnindexedProfileScreen()
        }
        composable<UnqueuedProfileRoute> {
            UnqueuedProfileScreen()
        }
        composable<UnannouncedProfileRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<UnannouncedProfileRoute>()

            UnannouncedProfileScreen()
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
                initialFeedListUIState = FeedListUIState.Loading,
                onNavigateToEvent = { nostrEventId ->
                    navController.navigate(
                        route = NostrEventDetailRoute(
                            nostrEventId = nostrEventId
                        )
                    )
                },
                onNavigateToWriteNewNote = {
                    navController.navigate(
                        route = WriteNewNoteRoute()
                    )
                },
                onNavigateToSearch = {
                    navController.navigate(
                        route = SearchRoute
                    )
                },
                nostrRepository = databaseNostrRepository
            )
        }
        composable<BlankRoute> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {

            }
        }
        composable<SearchRoute> {
            SearchScreen(
                initialSearchUIState = SearchUIState.Prompt,
                nostrRepository = databaseNostrRepository,
                searchRepository = searchRepository,
                onNavigateToProfile = { route ->
                    navController.navigate(
                        route = route
                    )
                },
                onNavigateToSearchResult = { route ->
                    navController.navigate(
                        route = route
                    )
                }
            )
        }
        composable<SearchResultRoute> {  backStackEntry ->
            val route = backStackEntry.toRoute<SearchResultRoute>()

            SearchResultScreen(
                searchQuery = route.query,
                nostrRepository = databaseNostrRepository,
                searchRepository = searchRepository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEvent = { route ->
                    navController.navigate(
                        route = route
                    )
                }
            )
        }
        composable<NostrEventDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<NostrEventDetailRoute>()

            NostrEventDetailScreen(
                initialNostrEventDetailUIState = NostrEventDetailUIState.Loading,
                nostrEventId = route.nostrEventId,
                nostrRepository = databaseNostrRepository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToWriteAReply = { nostrEventId ->
                    navController.navigate(
                        route = WriteNewNoteRoute(
                            inReplyToEventId = nostrEventId
                        )
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