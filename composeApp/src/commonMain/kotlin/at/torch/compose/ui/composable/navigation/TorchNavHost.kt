package at.torch.compose.ui.composable.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import at.torch.compose.database.repository.DatabaseMarmotRepository
import at.torch.compose.ui.composable.ChatRoomCreationScreen
import at.torch.compose.ui.composable.ChatRoomDetailScreen
import at.torch.compose.ui.composable.HomeScreen
import at.torch.compose.ui.composable.UnsyncedProfileScreen
import at.torch.compose.ui.composable.navigation.routes.ChatRoomCreationRoute
import at.torch.compose.ui.composable.navigation.routes.LoadingRoute
import at.torch.compose.ui.composable.navigation.routes.SovereignWalletStartupRoute
import at.torch.compose.ui.composable.navigation.routes.WriteNewNoteRoute
import at.torch.compose.ui.view.state.NavigationUIState
import co.touchlab.kermit.Logger
import fr.acinq.phoenix.PhoenixGlobal
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Composable
fun TorchNavHost(
    torchGlobal: at.torch.compose.TorchGlobal,
    phoenixGlobal: PhoenixGlobal,
    navController: NavHostController
) {
    val logger = Logger.withTag("AuxNavHost")
// TODO
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            logger.e("Caught exception: ${throwable.message}", throwable)
        }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    val applicationMainScope = CoroutineScope(Dispatchers.Main)

    val lifecycleOwner = LocalLifecycleOwner.current

    val auxDatabaseManager =
        _root_ide_package_.at.torch.compose.managers.AuxDatabaseManager(torchGlobal)

    val databaseNostrRepository =
        _root_ide_package_.at.torch.compose.database.repository.DatabaseNostrRepository(
            database = auxDatabaseManager.auxDatabase,
            applicationIOScope
        )
    val databaseChatRepository =
        _root_ide_package_.at.torch.compose.database.repository.DatabaseChatRepository(
            database = auxDatabaseManager.auxDatabase,
            applicationIOScope
        )

    val databaseMarmotRepository = DatabaseMarmotRepository(
        database = auxDatabaseManager.auxDatabase,
        applicationIOScope
    )

    val searchRepository =
        _root_ide_package_.at.torch.compose.database.repository.DatabaseSearchRepository(
            database = auxDatabaseManager.auxDatabase
        )

    val sovereignWalletViewModel: at.torch.compose.ui.view.model.SovereignWalletViewModel = viewModel(factory = at.torch.compose.ui.view.model.SovereignWalletViewModel.factory(
        phoenixGlobal = phoenixGlobal
    ))

    val navigationViewModel: at.torch.compose.ui.view.model.NavigationViewModel =  viewModel (
        factory = at.torch.compose.ui.view.model.NavigationViewModel.factory(
            activeWallet = sovereignWalletViewModel.activeWalletInUI,
            initialNavigationUIState = NavigationUIState.Loading(),
            nostrRepository = databaseNostrRepository,
            scope = applicationIOScope
        )
    )


    val notaryViewModel: at.torch.compose.ui.view.model.NotaryViewModel = viewModel(
        factory = at.torch.compose.ui.view.model.NotaryViewModel.factory(
            activeWalletStateFlow = sovereignWalletViewModel.activeWalletInUI,
            nostrRepository = databaseNostrRepository,
            chatRepository = databaseChatRepository,
            scope = applicationIOScope
        )
    )
    // TODO: Produce a notary UI Element...
    val synchronizationViewModel: at.torch.compose.ui.view.model.SynchronizationViewModel = viewModel(
        factory = at.torch.compose.ui.view.model.SynchronizationViewModel.factory(
            activeWalletStateFlow = sovereignWalletViewModel.activeWalletInUI,
            nostrRepository = databaseNostrRepository,
            chatRepository = databaseChatRepository,
            relayRepository = databaseNostrRepository,
            scope = applicationIOScope
        )
    )
    // TODO: Produce a synchronization UI element...

    LaunchedEffect(lifecycleOwner) {
        navigationViewModel.navigationUIState.collect { state ->
            when (state) {
                is NavigationUIState.Error -> {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute(
                            "Error screen"
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.StartupPhoenix -> {
                    navController.navigate(
                        route = SovereignWalletStartupRoute
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.Loading -> {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.LoadingRoute(
                            text = state.text
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.Landing -> {
                    navController.navigate(
                        route = at.torch.compose.ui.composable.navigation.routes.LandingRoute
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.UnsignedProfile -> {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.UnsignedProfileRoute(
                            state.unsignedNostrEvent.id
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.UnsyncedProfile -> {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.UnsyncedProfileRoute(
                            state.unsignedNostrEvent.pubKey
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.UnqueuedProfileSynchronization -> {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.UnqueuedProfileSynchronizationRoute(
                            unsignedNostrEventId = state.unsignedNostrEvent.id,
                            publicKey = state.unsignedNostrEvent.pubKey
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.UnindexedProfile -> {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.UnindexedProfileRoute(
                            state.nostrEvent.id
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.UnqueuedProfile -> {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.UnqueuedProfileRoute(
                            state.profile.nostrEventId
                        )
                    )
                }
                is NavigationUIState.UnannouncedProfile -> {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.UnannouncedProfileRoute(
                            state.broadcastNostrEventRequest.nostrEventId
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                is NavigationUIState.ProfileLoaded -> {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.SocialPreconditionRoute(
                            activeUserPubkey = state.publicKey
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                else -> {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute(
                            "Unsupported $state"
                        )
                    ) {
                        popUpTo(0)
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.LoadingRoute()
    ) {
        composable<SovereignWalletStartupRoute> {
            _root_ide_package_.at.torch.compose.ui.composable.SovereignWalletStartupScreen(
                sovereignWalletViewModel = sovereignWalletViewModel,
                onNavigateToWalletLandingPage = {
                    navController.navigate(
                        route = LoadingRoute(
                            text = "Loading Aux"
                        )
                    )
                },
                onNavigateToWalletIntroPage = {
                    navController.navigate(
                        route = LoadingRoute(
                            text = "Introducing... Aux"
                        )
                    )
                },
                onSuccessfulStartup = {
                    applicationIOScope.launch {
                        navigationViewModel.loadNostrProfile()
                    }
                },
                forceWalletId = null,
            )
        }
        composable<at.torch.compose.ui.composable.navigation.routes.LoadingRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<at.torch.compose.ui.composable.navigation.routes.LoadingRoute>()

            _root_ide_package_.at.torch.compose.ui.composable.LoadingScreen(
                text = route.text
            )
        }
        composable<at.torch.compose.ui.composable.navigation.routes.LandingRoute> {
            _root_ide_package_.at.torch.compose.ui.composable.LandingScreen(
                onNavigateToLearnMore = {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute(
                            "Learn more"
                        )
                    )
                },
                onNavigateToSignIn = {
                    navController.navigate(
                        route = at.torch.compose.ui.composable.navigation.routes.SignInRoute
                    )
                },
                onNavigateToCreateProfile = {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.CreateProfileRoute()
                    )
                }
            )
        }
        composable<at.torch.compose.ui.composable.navigation.routes.CreateProfileRoute> {
            _root_ide_package_.at.torch.compose.ui.composable.CreateProfileScreen(
                onNavigateToEndThis = {
                    navController.navigate(
                        route = at.torch.compose.ui.composable.navigation.routes.BlankRoute
                    ) {
                        popUpTo(0)
                    }
                },
                nostrRepository = databaseNostrRepository,
                marmotRepository = databaseMarmotRepository,
                writeSeed = { words ->
                    sovereignWalletViewModel.writeSeed(
                        words,
                        isRestoringWallet = false,
                        onSeedWritten = { walletId ->

                            sovereignWalletViewModel.loadSovereignData(walletId)
                            sovereignWalletViewModel.listAvailableWallets {
                                sovereignWalletViewModel.switchToWallet(walletId)
                                navController.navigate(
                                    route = SovereignWalletStartupRoute
                                )
                            }
                        }
                    )
                }
            )

        }
        composable<ChatRoomCreationRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ChatRoomCreationRoute>()

            ChatRoomCreationScreen(
                activeWalletStateFlow = sovereignWalletViewModel.activeWalletInUI,
                nostrRepository = databaseNostrRepository,
                chatRepository = databaseChatRepository,
                onNavigateToChatRoom = { chatRoomResultRoute ->
                    navController.navigate(
                        chatRoomResultRoute
                    ) {
                        popUpTo(route)
                    }
                }
            )
        }
        composable<WriteNewNoteRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<WriteNewNoteRoute>()
            _root_ide_package_.at.torch.compose.ui.composable.WriteNewNoteScreen(
                replyToNostrEventId = route.inReplyToEventId,
                activeUserPublicKey = route.activeUserPublicKey,
                quotedNostrEventId = route.quotedEventId,
                onNostrEventPublished = {
                    applicationMainScope.launch {
                        navController.popBackStack()
                    }
                },
                onNavigateToNostrEvent = { hexKey ->
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.NostrEventDetailRoute(
                            activeUserPublicKey = route.activeUserPublicKey,
                            nostrEventId = hexKey
                        )
                    )
                },
                nostrRepository = databaseNostrRepository
            )
        }
        composable<at.torch.compose.ui.composable.navigation.routes.SignInRoute> {
            _root_ide_package_.at.torch.compose.ui.composable.SignInToProfileScreen(
                nostrRepository = databaseNostrRepository
            )
        }
        composable<at.torch.compose.ui.composable.navigation.routes.UnsignedProfileRoute> { backStackEntry ->
            backStackEntry.toRoute<at.torch.compose.ui.composable.navigation.routes.UnsignedProfileRoute>()

            _root_ide_package_.at.torch.compose.ui.composable.UnsignedProfileScreen()
        }
        composable<at.torch.compose.ui.composable.navigation.routes.UnsyncedProfileRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<at.torch.compose.ui.composable.navigation.routes.UnsyncedProfileRoute>()
            UnsyncedProfileScreen(
                unsyncedProfilePublicKey = route.publicKey
            )
        }
        composable<at.torch.compose.ui.composable.navigation.routes.UnqueuedProfileSynchronizationRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<at.torch.compose.ui.composable.navigation.routes.UnqueuedProfileSynchronizationRoute>()
            _root_ide_package_.at.torch.compose.ui.composable.UnqueuedProfileSynchronizationScreen(
                unsignedNostrEventId = route.unsignedNostrEventId,
                profilePublicKey = route.publicKey,
                nostrRepository = databaseNostrRepository
            )
        }
        composable<at.torch.compose.ui.composable.navigation.routes.UnindexedProfileRoute> {
            _root_ide_package_.at.torch.compose.ui.composable.UnindexedProfileScreen()
        }
        composable<at.torch.compose.ui.composable.navigation.routes.UnqueuedProfileRoute> {
            _root_ide_package_.at.torch.compose.ui.composable.UnqueuedProfileScreen()
        }
        composable<at.torch.compose.ui.composable.navigation.routes.UnannouncedProfileRoute> { backStackEntry ->
            backStackEntry.toRoute<at.torch.compose.ui.composable.navigation.routes.UnannouncedProfileRoute>()

            _root_ide_package_.at.torch.compose.ui.composable.UnannouncedProfileScreen()
        }
        composable<at.torch.compose.ui.composable.navigation.routes.SocialPreconditionRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<at.torch.compose.ui.composable.navigation.routes.SocialPreconditionRoute>()
            _root_ide_package_.at.torch.compose.ui.composable.SocialPreconditionScreen(
                onNavigateToSkipForNow = {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.FeedRoute(
                            activeUserPublicKey = route.activeUserPubkey
                        )
                    )
                },
                onNavigateToInviteFriend = {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute(
                            "Invite Friend"
                        )
                    )
                },
                onNavigateToViewInvites = {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute(
                            "View invites"
                        )
                    )
                }
            )
        }
        composable<at.torch.compose.ui.composable.navigation.routes.FeedRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<at.torch.compose.ui.composable.navigation.routes.FeedRoute>()
            HomeScreen(
                activeUserPublicKey = route.activeUserPublicKey,
                onNavigateToEvent = { eventRoute ->
                    navController.navigate(
                        route = eventRoute
                    )
                },
                onNavigateToChatRoomCreation = {
                    navController.navigate(
                        route = ChatRoomCreationRoute(
                            activeUserPublicKey = route.activeUserPublicKey
                        )
                    )
                },
                onNavigateToSearch = {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.SearchRoute(
                            activeUserPublicKey = route.activeUserPublicKey
                        )
                    )
                },
                onNavigateToDirectMessageDetail = { chatRoomDetailRoute ->
                    navController.navigate(
                        route = chatRoomDetailRoute
                    )
                },
                nostrRepository = databaseNostrRepository,
                chatRepository = databaseChatRepository
            )
        }
        composable<at.torch.compose.ui.composable.navigation.routes.BlankRoute> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {

            }
        }
        composable<at.torch.compose.ui.composable.navigation.routes.ChatRoomDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<at.torch.compose.ui.composable.navigation.routes.ChatRoomDetailRoute>()

            ChatRoomDetailScreen(
                activeUserPublicKey = route.activeUserPublicKey,
                chatRoomId = route.chatRoomId,
                relayHint = route.relayHint,
                nostrRepository = databaseNostrRepository,
                chatRepository = databaseChatRepository
            )
        }
        composable<at.torch.compose.ui.composable.navigation.routes.SearchRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<at.torch.compose.ui.composable.navigation.routes.SearchRoute>()

            _root_ide_package_.at.torch.compose.ui.composable.SearchScreen(
                activeUserPublicKey = route.activeUserPublicKey,
                initialSearchUIState = at.torch.compose.ui.view.state.SearchUIState.Prompt,
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
        composable<at.torch.compose.ui.composable.navigation.routes.SearchResultRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<at.torch.compose.ui.composable.navigation.routes.SearchResultRoute>()

            _root_ide_package_.at.torch.compose.ui.composable.SearchResultScreen(
                activeUserPublicKey = route.activeUserPublicKey,
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
        composable<at.torch.compose.ui.composable.navigation.routes.NostrEventDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<at.torch.compose.ui.composable.navigation.routes.NostrEventDetailRoute>()

            _root_ide_package_.at.torch.compose.ui.composable.NostrEventDetailScreen(
                activeUserPublicKey = route.activeUserPublicKey,
                initialNostrEventDetailUIState = at.torch.compose.ui.view.state.NostrEventDetailUIState.Loading,
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
                            activeUserPublicKey = route.activeUserPublicKey,
                            inReplyToEventId = nostrEventId
                        )
                    )
                },
                onNavigateToEditProfile = {
                    navController.navigate(
                        route = _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute(
                            "Edit Profile"
                        )
                    )
                },
                onNavigateToQuoteNostrEvent = { nostrEventId ->
                    navController.navigate(
                        route = WriteNewNoteRoute(
                            activeUserPublicKey = route.activeUserPublicKey,
                            quotedEventId = nostrEventId
                        )
                    )
                },
                onNavigateToDirectMessage = { route ->
                    navController.navigate(
                        route = route
                    )
                },
            )
        }
        composable<at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute> { backStackEntry ->
            val route: at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute = backStackEntry.toRoute()
            _root_ide_package_.at.torch.compose.ui.composable.ImplementationPendingScreen(
                route.name
            )
        }
    }

}