package press.mantra.compose.ui.composable.navigation

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
import press.mantra.compose.MantraGlobal
import press.mantra.compose.database.repository.DatabaseChatRepository
import press.mantra.compose.database.repository.DatabaseMarmotRepository
import press.mantra.compose.database.repository.DatabaseNostrRepository
import press.mantra.compose.database.repository.DatabaseSearchRepository
import press.mantra.compose.managers.AuxDatabaseManager
import press.mantra.compose.ui.composable.ActiveProfileScreen
import press.mantra.compose.ui.composable.AddMemberToChatRoomConfirmationScreen
import press.mantra.compose.ui.composable.ChatRoomCreationScreen
import press.mantra.compose.ui.composable.ChatRoomDetailScreen
import press.mantra.compose.ui.composable.ChatRoomMessagingScreen
import press.mantra.compose.ui.composable.CreateProfileScreen
import press.mantra.compose.ui.composable.HomeScreen
import press.mantra.compose.ui.composable.ImplementationPendingScreen
import press.mantra.compose.ui.composable.KeyPackageManagementScreen
import press.mantra.compose.ui.composable.LandingScreen
import press.mantra.compose.ui.composable.LoadingScreen
import press.mantra.compose.ui.composable.NostrEventDetailScreen
import press.mantra.compose.ui.composable.SearchMemberToAddToChatRoomScreen
import press.mantra.compose.ui.composable.SearchResultScreen
import press.mantra.compose.ui.composable.SearchScreen
import press.mantra.compose.ui.composable.ShareProfileScreen
import press.mantra.compose.ui.composable.SignInToProfileScreen
import press.mantra.compose.ui.composable.SocialPreconditionScreen
import press.mantra.compose.ui.composable.SovereignWalletStartupScreen
import press.mantra.compose.ui.composable.UnannouncedProfileScreen
import press.mantra.compose.ui.composable.UnindexedProfileScreen
import press.mantra.compose.ui.composable.UnqueuedProfileScreen
import press.mantra.compose.ui.composable.UnqueuedProfileSynchronizationScreen
import press.mantra.compose.ui.composable.UnsignedProfileScreen
import press.mantra.compose.ui.composable.UnsyncedProfileScreen
import press.mantra.compose.ui.composable.WriteNewNoteScreen
import press.mantra.compose.ui.composable.navigation.routes.ActiveProfileRoute
import press.mantra.compose.ui.composable.navigation.routes.AddMemberToChatRoomConfirmationRoute
import press.mantra.compose.ui.composable.navigation.routes.BlankRoute
import press.mantra.compose.ui.composable.navigation.routes.ChatRoomCreationRoute
import press.mantra.compose.ui.composable.navigation.routes.ChatRoomDetailRoute
import press.mantra.compose.ui.composable.navigation.routes.ChatRoomMessagingRoute
import press.mantra.compose.ui.composable.navigation.routes.CreateProfileRoute
import press.mantra.compose.ui.composable.navigation.routes.HomeRoute
import press.mantra.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import press.mantra.compose.ui.composable.navigation.routes.KeyPackageManagementRoute
import press.mantra.compose.ui.composable.navigation.routes.LandingRoute
import press.mantra.compose.ui.composable.navigation.routes.LoadingRoute
import press.mantra.compose.ui.composable.navigation.routes.NostrEventDetailRoute
import press.mantra.compose.ui.composable.navigation.routes.SearchMemberToAddToChatRoomRoute
import press.mantra.compose.ui.composable.navigation.routes.SearchResultRoute
import press.mantra.compose.ui.composable.navigation.routes.SearchRoute
import press.mantra.compose.ui.composable.navigation.routes.ShareProfileRoute
import press.mantra.compose.ui.composable.navigation.routes.SignInRoute
import press.mantra.compose.ui.composable.navigation.routes.SocialPreconditionRoute
import press.mantra.compose.ui.composable.navigation.routes.SovereignWalletStartupRoute
import press.mantra.compose.ui.composable.navigation.routes.UnannouncedProfileRoute
import press.mantra.compose.ui.composable.navigation.routes.UnindexedProfileRoute
import press.mantra.compose.ui.composable.navigation.routes.UnqueuedProfileRoute
import press.mantra.compose.ui.composable.navigation.routes.UnqueuedProfileSynchronizationRoute
import press.mantra.compose.ui.composable.navigation.routes.UnsignedProfileRoute
import press.mantra.compose.ui.composable.navigation.routes.UnsyncedProfileRoute
import press.mantra.compose.ui.composable.navigation.routes.WriteNewNoteRoute
import press.mantra.compose.ui.view.model.NavigationViewModel
import press.mantra.compose.ui.view.model.NotaryViewModel
import press.mantra.compose.ui.view.model.SovereignWalletViewModel
import press.mantra.compose.ui.view.model.SynchronizationViewModel
import press.mantra.compose.ui.view.state.NavigationUIState
import press.mantra.compose.ui.view.state.NostrEventDetailUIState
import press.mantra.compose.ui.view.state.SearchUIState
import co.touchlab.kermit.Logger
import fr.acinq.phoenix.PhoenixGlobal
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Composable
fun MantraNavHost(
    mantraGlobal: MantraGlobal,
    phoenixGlobal: PhoenixGlobal,
    navController: NavHostController
) {
    val logger = Logger.withTag("MantraNavHost")

    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        logger.e("Caught exception: ${throwable.message}", throwable)
    }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    val applicationMainScope = CoroutineScope(Dispatchers.Main)

    val lifecycleOwner = LocalLifecycleOwner.current

    val auxDatabaseManager =
        AuxDatabaseManager(mantraGlobal)

    val databaseNostrRepository = DatabaseNostrRepository(
        database = auxDatabaseManager.auxDatabase,
        applicationIOScope
    )

    val databaseChatRepository = DatabaseChatRepository(
        database = auxDatabaseManager.auxDatabase,
        applicationIOScope
    )

    val databaseMarmotRepository = DatabaseMarmotRepository(
        database = auxDatabaseManager.auxDatabase,
        applicationIOScope
    )

    val searchRepository = DatabaseSearchRepository(
        database = auxDatabaseManager.auxDatabase
    )

    val sovereignWalletViewModel: SovereignWalletViewModel = viewModel(
        factory = SovereignWalletViewModel.factory(
            phoenixGlobal = phoenixGlobal
        )
    )

    val navigationViewModel: NavigationViewModel =  viewModel (
        factory = NavigationViewModel.factory(
            activeWallet = sovereignWalletViewModel.activeWalletInUI,
            initialNavigationUIState = NavigationUIState.Loading(),
            nostrRepository = databaseNostrRepository,
            scope = applicationIOScope
        )
    )

    val notaryViewModel: NotaryViewModel = viewModel(
        factory = NotaryViewModel.factory(
            activeWalletStateFlow = sovereignWalletViewModel.activeWalletInUI,
            nostrRepository = databaseNostrRepository,
            chatRepository = databaseChatRepository,
            marmotRepository = databaseMarmotRepository,
            scope = applicationIOScope
        )
    )

    // TODO: Produce a notary UI Element...
    val synchronizationViewModel: SynchronizationViewModel = viewModel(
        factory = SynchronizationViewModel.factory(
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
                        route = ImplementationPendingRoute(
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
                        route = LoadingRoute(
                            text = state.text
                        )
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
                            publicKey = state.unsignedNostrEvent.pubKey
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
                        route = SocialPreconditionRoute(
                            activeUserPubkey = state.publicKey
                        )
                    ) {
                        popUpTo(0)
                    }
                }
                else -> {
                    navController.navigate(
                        route = ImplementationPendingRoute(
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
        startDestination = LoadingRoute()
    ) {
        composable<SovereignWalletStartupRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SovereignWalletStartupRoute>()

            SovereignWalletStartupScreen(
                sovereignWalletViewModel = sovereignWalletViewModel,
                onNavigateToWalletLandingPage = {
                    navController.navigate(
                        route = LoadingRoute(
                            text = "Loading... Torch"
                        )
                    )
                },
                onNavigateToWalletIntroPage = {
                    navController.navigate(
                        route = LoadingRoute(
                            text = "Introducing... Torch"
                        )
                    )
                    applicationIOScope.launch {
                        navigationViewModel.loadNostrProfile(route)
                    }
                },
                onSuccessfulStartup = {
                    applicationIOScope.launch {
                        navigationViewModel.loadNostrProfile(route)
                    }
                },
                forceWalletId = null,
            )
        }
        composable<LoadingRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<LoadingRoute>()

            LoadingScreen(
                text = route.text
            )
        }
        composable<LandingRoute> {
            LandingScreen(
                onNavigateToLearnMore = {
                    navController.navigate(
                        route = ImplementationPendingRoute(
                            "Learn more"
                        )
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
                onNavigateToEndThis = {
                    navController.navigate(
                        route = BlankRoute
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
            WriteNewNoteScreen(
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
                        route = NostrEventDetailRoute(
                            activeUserPublicKey = route.activeUserPublicKey,
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
            backStackEntry.toRoute<UnsignedProfileRoute>()

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
            backStackEntry.toRoute<UnannouncedProfileRoute>()

            UnannouncedProfileScreen()
        }
        composable<SocialPreconditionRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SocialPreconditionRoute>()
            SocialPreconditionScreen(
                onNavigateToSkipForNow = {
                    navController.navigate(
                        route = HomeRoute(
                            activeUserPublicKey = route.activeUserPubkey
                        )
                    )
                },
                onNavigateToInviteFriend = {
                    navController.navigate(
                        route = ImplementationPendingRoute(
                            "Invite Friend"
                        )
                    )
                },
                onNavigateToViewInvites = {
                    navController.navigate(
                        route = ImplementationPendingRoute(
                            "View invites"
                        )
                    )
                }
            )
        }
        composable<HomeRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<HomeRoute>()
            HomeScreen(
                activeUserPublicKey = route.activeUserPublicKey,
                onNavigateToRoute = { eventRoute ->
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
                        route = SearchRoute(
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
        composable<BlankRoute> {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {

            }
        }
        composable<ActiveProfileRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ActiveProfileRoute>()

            ActiveProfileScreen(
                activeUserPublicKey = route.activeUserPublicKey,
                nostrEventId = route.nostrEventId,
                nostrRepository = databaseNostrRepository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToRoute = { route ->
                    navController.navigate(
                        route = route
                    )
                }
            )
        }
        composable<ShareProfileRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ShareProfileRoute>()

            ShareProfileScreen(
                activeUserPublicKey = route.activeUserPublicKey,
                nostrEventId = route.nostrEventId,
                nostrRepository = databaseNostrRepository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToRoute = { route ->
                    navController.navigate(
                        route = route
                    )
                }
            )
        }
        composable<KeyPackageManagementRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<KeyPackageManagementRoute>()

            KeyPackageManagementScreen(
                activeUserPublicKey = route.activeUserPublicKey,
                nostrEventId = route.nostrEventId,
                marmotRepository = databaseMarmotRepository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToRoute = { route ->
                    navController.navigate(
                        route = route
                    )
                }
            )
        }
        composable<ChatRoomMessagingRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ChatRoomMessagingRoute>()

            ChatRoomMessagingScreen(
                activeUserPublicKey = route.activeUserPublicKey,
                chatRoomId = route.chatRoomId,
                relayHint = route.relayHint,
                nostrRepository = databaseNostrRepository,
                chatRepository = databaseChatRepository,
                onNavigateToRouteAndPopUpInclusive = { chatRoomDetailRoute ->
                    navController.navigate(
                        route = chatRoomDetailRoute
                    ) {
                        popUpTo(route) {
                            inclusive = true
                        }
                    }
                },
                onNavigateToRoute = { actionRoute ->
                    navController.navigate(
                        route = actionRoute
                    )
                }
            )
        }
        composable<ChatRoomDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ChatRoomDetailRoute>()

            ChatRoomDetailScreen(
                activeUserPublicKey = route.activeUserPublicKey,
                chatRoomId = route.chatRoomId,
                relayHint = route.relayHint,
                nostrRepository = databaseNostrRepository,
                chatRepository = databaseChatRepository,
                onNavigateToRoute = { actionRoute ->
                    navController.navigate(
                        route = actionRoute
                    )
                },
                onPopBackToRoute = { popRoute ->
                    navController.popBackStack(
                        route = popRoute,
                        inclusive = false
                    )
                }
            )
        }
        composable<SearchMemberToAddToChatRoomRoute> {  backStackEntry ->
            val route = backStackEntry.toRoute<SearchMemberToAddToChatRoomRoute>()

            SearchMemberToAddToChatRoomScreen(
                activeUserPublicKey = route.activeUserPublicKey,
                chatRoomId = route.chatRoomId,
                relayHint = route.relayHint,
                nostrRepository = databaseNostrRepository,
                chatRepository = databaseChatRepository,
                onNavigateToRoute = { chatRoomDetailRoute ->
                    navController.navigate(
                        route = chatRoomDetailRoute
                    )
                },
            )
        }
        composable<AddMemberToChatRoomConfirmationRoute> {  backStackEntry ->
            val route = backStackEntry.toRoute<AddMemberToChatRoomConfirmationRoute>()

            AddMemberToChatRoomConfirmationScreen(
                activeUserPublicKey = route.activeUserPublicKey,
                chatRoomId = route.chatRoomId,
                profilePublicKey = route.profilePublicKey,
                relayHint = route.relayHint,
                nostrRepository = databaseNostrRepository,
                chatRepository = databaseChatRepository,
                onInviteSent = {
                    navController.popBackStack(
                        route = SearchMemberToAddToChatRoomRoute(
                            activeUserPublicKey = route.activeUserPublicKey,
                            chatRoomId = route.chatRoomId,
                            relayHint = route.relayHint
                        ),
                        inclusive = true
                    )
                },
            )
        }
        composable<SearchRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SearchRoute>()

            SearchScreen(
                activeUserPublicKey = route.activeUserPublicKey,
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
        composable<SearchResultRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SearchResultRoute>()

            SearchResultScreen(
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
        composable<NostrEventDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<NostrEventDetailRoute>()

            NostrEventDetailScreen(
                activeUserPublicKey = route.activeUserPublicKey,
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
                            activeUserPublicKey = route.activeUserPublicKey,
                            inReplyToEventId = nostrEventId
                        )
                    )
                },
                onNavigateToEditProfile = {
                    navController.navigate(
                        route = ImplementationPendingRoute(
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
                onNavigateToDirectMessage = { chatRoom ->
                    navController.navigate(
                        route = chatRoom
                    ) {
                        popUpTo(route) {
                            inclusive = true
                        }
                    }
                },
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