package ac.cord.auxiliary.compose.ui.composable.widgets.detail

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.ChatRoomDetailRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.Route
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileAvatar
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.view.model.FeedListViewModel
import ac.cord.auxiliary.compose.ui.view.model.FollowersListViewModel
import ac.cord.auxiliary.compose.ui.view.model.FollowingListViewModel
import ac.cord.auxiliary.compose.ui.view.model.MetadataEventDetailType
import ac.cord.auxiliary.compose.ui.view.model.MetadataEventDetailViewModel
import ac.cord.auxiliary.compose.ui.view.state.FeedListUIState
import ac.cord.auxiliary.compose.ui.view.state.FollowersListUIState
import ac.cord.auxiliary.compose.ui.view.state.FollowingListUIState
import ac.cord.auxiliary.compose.ui.view.state.MetadataEventDetailUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MetadataEventDetail(
    localNostrEvent: LocalNostrEvent,
    onNavigateBack: () -> Unit,
    onNavigateToEvent: (Route) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToChatRoom: (Route) -> Unit,
    nostrRepository: NostrRepository
) {
    val scope = rememberCoroutineScope()

    val profile = localNostrEvent.profile
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val metadataEventDetailViewModel: MetadataEventDetailViewModel = viewModel(
        factory = MetadataEventDetailViewModel.factory(
            publicKey = localNostrEvent.nostrEvent.pubKey,
            nostrRepository = nostrRepository,
            pagerState = rememberPagerState { MetadataEventDetailType.entries.size }
        ),
    )

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBackIosNew,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                title = {
                    Column(
                        modifier = Modifier,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = profile?.displayName ?: profile?.userName ?: localNostrEvent.nostrEvent.pubKey,
                            style = MaterialTheme.typography.titleLarge
                        )

                        profile?.nip05?.let {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = "Lightning Bolt",
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                        }

                    }
                },

                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileAvatar(
                                size = 75.dp,
                                profile = profile,
                                publicKey = localNostrEvent.nostrEvent.pubKey
                            )


                            Spacer(
                                modifier = Modifier.weight(1f)
                            )

                            when(val metadataEventDetailUIState = metadataEventDetailViewModel.metadataEventDetailUIState) {
                                MetadataEventDetailUIState.Error -> {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = "Something went wrong"
                                    )
                                }
                                is MetadataEventDetailUIState.Loaded -> {

                                    if (metadataEventDetailViewModel.isActionPending.value) {
                                        LoadingIndicator()
                                    } else {
                                        if (metadataEventDetailUIState.isActiveUser) {
                                            Button(
                                                onClick = onNavigateToEditProfile
                                            ) {
                                                Text(
                                                    "Edit Profile"
                                                )
                                            }

                                        } else if (metadataEventDetailUIState.followedByActiveUserConnection != null) {
                                            Button(
                                                onClick = {
                                                    // Unfollow
                                                    metadataEventDetailViewModel.unfollow(
                                                        metadataEventDetailUIState.followedByActiveUserConnection
                                                    )
                                                }
                                            ) {
                                                Text(
                                                    "Unfollow"
                                                )
                                            }
                                        } else {
                                            // flow
                                            Button(
                                                onClick = {
                                                    metadataEventDetailViewModel.follow()
                                                }
                                            ) {
                                                Text(
                                                    text = if (metadataEventDetailUIState.followingActiveUserConnection != null) {
                                                        "follow back"
                                                    } else {
                                                        "follow"
                                                    }
                                                )
                                            }
                                        }
                                    }


                                }
                                MetadataEventDetailUIState.Loading -> {
                                    LoadingIndicator()
                                }
                            }

                            IconButton(
                                onClick = {
                                    onNavigateToChatRoom.invoke(
                                        ChatRoomDetailRoute(
                                            directMessageIdentifier = localNostrEvent.nostrEvent.pubKey
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.Mail,
                                    contentDescription = "Direct Message"
                                )
                            }

                        }
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(
                                horizontal = 20.dp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            text = profile?.about ?: ""
                        )
                    }
                }

                stickyHeader {
                    PrimaryScrollableTabRow(
                        modifier = Modifier.padding(
                            horizontal = 10.dp
                        ),
                        edgePadding = 10.dp,
                        selectedTabIndex = metadataEventDetailViewModel.pagerState.currentPage,
                    ) {
                        MetadataEventDetailType.entries.forEachIndexed { index, destination ->
                            Tab(
                                selected = metadataEventDetailViewModel.pagerState.currentPage == index,
                                onClick = {
                                    scope.launch {
                                        metadataEventDetailViewModel.pagerState.animateScrollToPage(destination.ordinal)
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
                }

                item {
                    Column(
                        modifier = Modifier.fillParentMaxSize()
                    ) {
                        HorizontalPager(
                            state = metadataEventDetailViewModel.pagerState,
                        ) { pageIndex ->
                            val metadataEventDetailType = MetadataEventDetailType.entries[pageIndex]

                            when (metadataEventDetailType) {
                                MetadataEventDetailType.Posts, MetadataEventDetailType.Replies,
                                MetadataEventDetailType.Articles, MetadataEventDetailType.Zaps,
                                MetadataEventDetailType.Photos, MetadataEventDetailType.Shorts, MetadataEventDetailType.Videos,
                                MetadataEventDetailType.Bookmarks, MetadataEventDetailType.Reports, MetadataEventDetailType.Relays -> {
                                    val feedListViewModel: FeedListViewModel = viewModel(
                                        key = metadataEventDetailType.name,
                                        factory = FeedListViewModel.factory(
                                            initialFeedListUIState = FeedListUIState.Loading,
                                            synchronizationFilter = metadataEventDetailViewModel.getSynchronizationFilter(metadataEventDetailType),
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
                                // TODO: Support gallery feeds...
                                MetadataEventDetailType.Following -> {
                                    val followingListViewModel: FollowingListViewModel = viewModel(
                                        key = metadataEventDetailType.name,
                                        factory = FollowingListViewModel.factory(
                                            initialFeedListUIState = FollowingListUIState.Loading,
                                            publicKey = localNostrEvent.nostrEvent.pubKey,
                                            synchronizationFilter = metadataEventDetailViewModel.getSynchronizationFilter(metadataEventDetailType),
                                            nostrRepository = nostrRepository
                                        ),
                                    )


                                    key(followingListViewModel.followingListUIState) {
                                        followingListViewModel.RenderFeed(
                                            onNavigateToEvent = onNavigateToEvent
                                        )
                                    }

                                    key(true) {
                                        followingListViewModel.initiate()
                                    }
                                }
                                MetadataEventDetailType.Followers -> {
                                    val followersListViewModel: FollowersListViewModel = viewModel(
                                        key = metadataEventDetailType.name,
                                        factory = FollowersListViewModel.factory(
                                            initialFeedListUIState = FollowersListUIState.Loading,
                                            publicKey = localNostrEvent.nostrEvent.pubKey,
                                            synchronizationFilter = metadataEventDetailViewModel.getSynchronizationFilter(metadataEventDetailType),
                                            nostrRepository = nostrRepository
                                        ),
                                    )


                                    key(followersListViewModel.followersListUIState) {
                                        followersListViewModel.RenderFeed(
                                            onNavigateToEvent = onNavigateToEvent
                                        )
                                    }

                                    key(true) {
                                        followersListViewModel.initiate()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            key(true) {
                metadataEventDetailViewModel.initiate()
            }
        }
    }
}

@Preview
@Composable
private fun MetadataEventEventDetailPreview() {

    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            MetadataEventDetail(
                localNostrEvent = LocalNostrEvent(
                    nostrEvent = NostrEvent(
                        id = "eventId",
                        pubKey = "publicKey",
                        content = """Profile""".trimIndent(),
                        tags = emptyArray(),
                        sig = "",
                        kind = MetadataEvent.KIND
                    ),
                    profile = Profile(
                        publicKey = "pubKey",
                        displayName = "John Doe",
                        nip05 = "john@doe.com",
                        about = "I am the stone that the builder refused.",
                        nostrEventId = "nostrEventId"
                    )
                ),
                onNavigateBack = {},
                onNavigateToEvent = {},
                onNavigateToEditProfile = {},
                onNavigateToChatRoom = {},
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY
            )
        }
    }
}