package at.torch.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.torch.compose.ui.view.model.HomeScreenType
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    activeUserPublicKey: HexKey,
    initialHomeScreenUIState: at.torch.compose.ui.view.state.HomeScreenUIState = at.torch.compose.ui.view.state.HomeScreenUIState.Loading,
    onNavigateToEvent: (at.torch.compose.ui.composable.navigation.routes.Route) -> Unit,
    onNavigateToDirectMessageDetail: (at.torch.compose.ui.composable.navigation.routes.Route) -> Unit,
    onNavigateToChatRoomCreation: () -> Unit,
    onNavigateToSearch: () -> Unit,
    nostrRepository: at.torch.compose.repository.NostrRepository,
    chatRepository: at.torch.compose.repository.ChatRepository
) {
    val scope = rememberCoroutineScope()

    val homeScreenViewModel: at.torch.compose.ui.view.model.HomeViewModel = viewModel(
        factory = at.torch.compose.ui.view.model.HomeViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            initialHomeScreenUIState = initialHomeScreenUIState,
            nostrRepository = nostrRepository,
            pagerState = rememberPagerState { HomeScreenType.entries.size }
        ),
    )

    when(val homeScreenUIState = homeScreenViewModel.homeScreenUIState) {
        at.torch.compose.ui.view.state.HomeScreenUIState.Error -> {
            Scaffold { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Something went wrong"
                    )

                    Spacer(
                        modifier = Modifier.weight(2f)
                    )
                }
            }

        }
        is at.torch.compose.ui.view.state.HomeScreenUIState.Loaded -> {
            Scaffold(
                modifier = Modifier,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.primary
                        ),
                        title = {
                            Text(
                                "Aux"
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    onNavigateToEvent.invoke(
                                        _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.NostrEventDetailRoute(
                                            activeUserPublicKey = activeUserPublicKey,
                                            nostrEventId = homeScreenUIState.profileWithFollowing.nostrEvent.id
                                        )
                                    )
                                }
                            ) {
                                _root_ide_package_.at.torch.compose.ui.composable.widgets.profile.ProfileAvatar(
                                    profile = homeScreenUIState.profileWithFollowing.profile,
                                    publicKey = homeScreenUIState.profileWithFollowing.profile.publicKey
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    onNavigateToSearch.invoke()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }
                        }
                    )

                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = onNavigateToChatRoomCreation,
                        icon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "New Chat"
                            )
                        },
                        text = {
                            Text("New Chat")
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize()
                ) {
                    PrimaryScrollableTabRow(
                        modifier = Modifier.padding(10.dp),
                        edgePadding = 10.dp,
                        selectedTabIndex = homeScreenViewModel.pagerState.currentPage,
                    ) {
                        HomeScreenType.entries.forEachIndexed { index, destination ->
                            Tab(
                                selected = homeScreenViewModel.pagerState.currentPage == index,
                                onClick = {
                                    scope.launch {
                                        homeScreenViewModel.pagerState.animateScrollToPage(destination.ordinal)
                                    }
                                },
                                text = {
                                    Text(
                                        text = destination.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        HorizontalPager(
                            state = homeScreenViewModel.pagerState,
                        ) { pageIndex ->
                            when (val homeScreenTypeType = HomeScreenType.entries[pageIndex]) {
                                HomeScreenType.Messages -> {
                                    val chatRoomListViewModel: at.torch.compose.ui.view.model.ChatRoomListViewModel = viewModel(
                                        key = homeScreenTypeType.name,
                                        factory = at.torch.compose.ui.view.model.ChatRoomListViewModel.factory(
                                            initialChatRoomListUIState = at.torch.compose.ui.view.state.ChatRoomListUIState.Loading,
                                            publicKey = homeScreenUIState.profileWithFollowing.profile.publicKey,
                                            synchronizationFilter = homeScreenViewModel.getSynchronizationFilter(
                                                homeScreenTypeType,
                                                profileWithFollowing = homeScreenUIState.profileWithFollowing
                                            ),
                                            nostrRepository = nostrRepository,
                                            chatRepository = chatRepository
                                        ),
                                    )

                                    key(true) {
                                        chatRoomListViewModel.RenderFeed(
                                            onNavigateToDirectMessageDetail = onNavigateToDirectMessageDetail
                                        )
                                    }

                                    key(true) {
                                        chatRoomListViewModel.initiate()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        at.torch.compose.ui.view.state.HomeScreenUIState.Loading -> {
            _root_ide_package_.at.torch.compose.ui.composable.widgets.LoadingDataIndicator()
        }
    }

    key(true) {
        homeScreenViewModel.initiate()
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    _root_ide_package_.at.torch.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            HomeScreen(
                activeUserPublicKey = "",
                initialHomeScreenUIState = at.torch.compose.ui.view.state.HomeScreenUIState.Loaded(
                    profileWithFollowing = _root_ide_package_.at.torch.compose.database.model.intermdiate.LocalProfileWithFollowing(
                        nostrEvent = _root_ide_package_.at.torch.compose.database.model.NostrEvent(
                            id = "eventId",
                            pubKey = "publicKey",
                            content = """Lorem Ipsum is simply dummy text of the printing and typesetting industry. 
                                
Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. 
                                
It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.""".trimIndent(),
                            tags = emptyArray(),
                            sig = "",
                            kind = TextNoteEvent.KIND
                        ),
                        profile = _root_ide_package_.at.torch.compose.database.model.Profile(
                            publicKey = "pubKey",
                            displayName = "John Doe",
                            nostrEventId = "nostrEventId"
                        ),
                        following = emptyList()
                    )
                ),
                onNavigateToEvent = {},
                onNavigateToChatRoomCreation = {},
                onNavigateToSearch = {},
                onNavigateToDirectMessageDetail = {},
                nostrRepository = at.torch.compose.repository.NostrRepository.NO_OP_NOSTR_REPOSITORY,
                chatRepository = at.torch.compose.repository.ChatRepository.NO_OP_CHAT_REPOSITORY
            )
        }
    }
}