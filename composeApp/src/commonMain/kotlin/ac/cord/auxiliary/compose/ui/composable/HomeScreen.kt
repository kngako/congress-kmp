package ac.cord.auxiliary.compose.ui.composable

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.ChatRoomDetailRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.NostrEventDetailRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.Route
import ac.cord.auxiliary.compose.ui.composable.widgets.LoadingDataIndicator
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileAvatar
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.view.model.FeedListViewModel
import ac.cord.auxiliary.compose.ui.view.model.HomeScreenType
import ac.cord.auxiliary.compose.ui.view.model.HomeViewModel
import ac.cord.auxiliary.compose.ui.view.model.ChatRoomListViewModel
import ac.cord.auxiliary.compose.ui.view.state.FeedListUIState
import ac.cord.auxiliary.compose.ui.view.state.HomeScreenUIState
import ac.cord.auxiliary.compose.ui.view.state.ChatRoomListUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
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
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    initialHomeScreenUIState: HomeScreenUIState = HomeScreenUIState.Loading,
    onNavigateToEvent: (Route) -> Unit,
    onNavigateToDirectMessageDetail: (Route) -> Unit,
    onNavigateToWriteNewNote: () -> Unit,
    onNavigateToSearch: () -> Unit,
    nostrRepository: NostrRepository,
) {
    val scope = rememberCoroutineScope()

    val homeScreenViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            initialHomeScreenUIState = initialHomeScreenUIState,
            nostrRepository = nostrRepository,
            pagerState = rememberPagerState { HomeScreenType.entries.size }
        ),
    )

    when(val homeScreenUIState = homeScreenViewModel.homeScreenUIState) {
        HomeScreenUIState.Error -> {
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
        is HomeScreenUIState.Loaded -> {
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
                                        NostrEventDetailRoute(
                                            nostrEventId = homeScreenUIState.profileWithFollowing.nostrEvent.id
                                        )
                                    )
                                }
                            ) {
                                ProfileAvatar(
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
                        onClick = onNavigateToWriteNewNote,
                        icon = {
                            Icon(
                                Icons.Default.Create,
                                contentDescription = "Write"
                            )
                        },
                        text = {
                            Text("Write")
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
                                HomeScreenType.Following, HomeScreenType.Mentions -> {
                                    val feedListViewModel: FeedListViewModel = viewModel(
                                        key = homeScreenTypeType.name,
                                        factory = FeedListViewModel.factory(
                                            initialFeedListUIState = FeedListUIState.Loading,
                                            synchronizationFilter = homeScreenViewModel.getSynchronizationFilter(
                                                homeScreenTypeType,
                                                profileWithFollowing = homeScreenUIState.profileWithFollowing
                                            ),
                                            nostrRepository = nostrRepository
                                        ),
                                    )

                                    key(feedListViewModel.feedListUIState) {
                                        feedListViewModel.RenderFeed(
                                            onNavigateToEvent = onNavigateToEvent
                                        )
                                    }

                                    key(true) {
                                        feedListViewModel.initiate()
                                    }
                                }
                                HomeScreenType.Messages -> {
                                    val chatRoomListViewModel: ChatRoomListViewModel = viewModel(
                                        key = homeScreenTypeType.name,
                                        factory = ChatRoomListViewModel.factory(
                                            initialChatRoomListUIState = ChatRoomListUIState.Loading,
                                            publicKey = homeScreenUIState.profileWithFollowing.profile.publicKey,
                                            synchronizationFilter = homeScreenViewModel.getSynchronizationFilter(
                                                homeScreenTypeType,
                                                profileWithFollowing = homeScreenUIState.profileWithFollowing
                                            ),
                                            nostrRepository = nostrRepository
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
        HomeScreenUIState.Loading -> {
            LoadingDataIndicator()
        }
    }

    key(true) {
        homeScreenViewModel.initiate()
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            HomeScreen(
                initialHomeScreenUIState = HomeScreenUIState.Loaded(
                    profileWithFollowing = LocalProfileWithFollowing(
                        nostrEvent = NostrEvent(
                            id = "eventId",
                            pubKey = "publicKey",
                            content = """Lorem Ipsum is simply dummy text of the printing and typesetting industry. 
                                
Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. 
                                
It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.""".trimIndent(),
                            tags = emptyArray(),
                            sig = "",
                            kind = TextNoteEvent.KIND
                        ),
                        profile = Profile(
                            publicKey = "pubKey",
                            displayName = "John Doe",
                            nostrEventId = "nostrEventId"
                        ),
                        following = emptyList()
                    )
                ),
                onNavigateToEvent = {},
                onNavigateToWriteNewNote = {},
                onNavigateToSearch = {},
                onNavigateToDirectMessageDetail = {},
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY
            )
        }
    }
}