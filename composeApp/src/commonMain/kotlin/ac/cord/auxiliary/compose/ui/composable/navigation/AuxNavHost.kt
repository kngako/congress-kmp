package ac.cord.auxiliary.compose.ui.composable.navigation

import ac.cord.auxiliary.compose.AuxGlobal
import ac.cord.auxiliary.compose.database.repository.DatabaseNostrRepository
import ac.cord.auxiliary.compose.database.repository.DatabaseSearchRepository
import ac.cord.auxiliary.compose.managers.DatabaseManager
import ac.cord.auxiliary.compose.ui.composable.CreateProfileScreen
import ac.cord.auxiliary.compose.ui.composable.HomeScreen
import ac.cord.auxiliary.compose.ui.composable.ImplementationPendingScreen
import ac.cord.auxiliary.compose.ui.composable.LandingScreen
import ac.cord.auxiliary.compose.ui.composable.LoadingScreen
import ac.cord.auxiliary.compose.ui.composable.NostrEventDetailScreen
import ac.cord.auxiliary.compose.ui.composable.SearchResultScreen
import ac.cord.auxiliary.compose.ui.composable.SearchScreen
import ac.cord.auxiliary.compose.ui.composable.SignInToProfileScreen
import ac.cord.auxiliary.compose.ui.composable.SocialPreconditionScreen
import ac.cord.auxiliary.compose.ui.composable.UnannouncedProfileScreen
import ac.cord.auxiliary.compose.ui.composable.UnindexedProfileScreen
import ac.cord.auxiliary.compose.ui.composable.UnqueuedProfileScreen
import ac.cord.auxiliary.compose.ui.composable.UnqueuedProfileSynchronizationScreen
import ac.cord.auxiliary.compose.ui.composable.UnsignedProfileScreen
import ac.cord.auxiliary.compose.ui.composable.UnsyncedProfileScreen
import ac.cord.auxiliary.compose.ui.composable.WriteNewNoteScreen
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.BlankRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.CreateProfileRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.FeedRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.LandingRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.LoadingRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.NostrEventDetailRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.SearchResultRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.SearchRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.SignInRoute
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.SocialPreconditionRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.UnannouncedProfileRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.UnindexedProfileRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.UnqueuedProfileRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.UnqueuedProfileSynchronizationRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.UnsignedProfileRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.UnsyncedProfileRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.WriteNewNoteRoute
import ac.cord.auxiliary.compose.ui.view.model.NavigationViewModel
import ac.cord.auxiliary.compose.ui.view.state.FeedListUIState
import ac.cord.auxiliary.compose.ui.view.state.NavigationUIState
import ac.cord.auxiliary.compose.ui.view.state.NostrEventDetailUIState
import ac.cord.auxiliary.compose.ui.view.state.SearchUIState
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

    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            logger.e("Caught exception: ${throwable.message}", throwable)
        }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    val applicationMainScope = CoroutineScope(Dispatchers.Main)

    val lifecycleOwner = LocalLifecycleOwner.current

    val databaseManager = DatabaseManager(auxGlobal)

    val databaseNostrRepository = DatabaseNostrRepository(
        database = databaseManager.auxDatabase,
        applicationIOScope
    )
    val searchRepository = DatabaseSearchRepository(
        database = databaseManager.auxDatabase
    )




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
        composable<WriteNewNoteRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<WriteNewNoteRoute>()
            WriteNewNoteScreen(
                replyToNostrEventId = route.inReplyToEventId,
                quotedNostrEventId = route.quotedEventId,
                onNostrEventPublished = {
                    applicationMainScope.launch {
                        navController.popBackStack()
                    }
                },
                onNavigateToNostrEvent = { hexKey ->
                    navController.navigate(
                        route = NostrEventDetailRoute(
                            nostrEventId = hexKey
                        )
                    )
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
            HomeScreen(
                onNavigateToEvent = { eventRoute ->
                    navController.navigate(
                        route = eventRoute
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
                onNavigateToEvent = { route ->
                    navController.navigate(
                        route = route
                    )
                },
                onNavigateToWriteAReply = { nostrEventId ->
                    navController.navigate(
                        route = WriteNewNoteRoute(
                            inReplyToEventId = nostrEventId
                        )
                    )
                },
                onNavigateToEditProfile = {
                    navController.navigate(
                        route = ImplementationPendingRoute("Edit Profile")
                    )
                },
                onNavigateToQuoteNostrEvent = { nostrEventId ->
                    navController.navigate(
                        route = WriteNewNoteRoute(
                            quotedEventId = nostrEventId
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