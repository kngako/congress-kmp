package at.torch.compose.ui.composable.widgets.detail

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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MetadataEventDetail(
    localNostrEvent: at.torch.compose.database.model.intermdiate.LocalNostrEvent,
    activeUserPublicKey: HexKey,
    onNavigateBack: () -> Unit,
    onNavigateToEvent: (at.torch.compose.ui.composable.navigation.routes.Route) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToChatRoom: (at.torch.compose.ui.composable.navigation.routes.Route) -> Unit,
    nostrRepository: at.torch.compose.repository.NostrRepository
) {
    val scope = rememberCoroutineScope()

    val profile = localNostrEvent.profile
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val metadataEventDetailViewModel: at.torch.compose.ui.view.model.MetadataEventDetailViewModel = viewModel(
        factory = at.torch.compose.ui.view.model.MetadataEventDetailViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            publicKey = localNostrEvent.nostrEvent.pubKey,
            nostrRepository = nostrRepository,
            pagerState = rememberPagerState { at.torch.compose.ui.view.model.MetadataEventDetailType.entries.size }
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
                            _root_ide_package_.at.torch.compose.ui.composable.widgets.profile.ProfileAvatar(
                                size = 75.dp,
                                profile = profile,
                                publicKey = localNostrEvent.nostrEvent.pubKey
                            )


                            Spacer(
                                modifier = Modifier.weight(1f)
                            )

                            when(val metadataEventDetailUIState = metadataEventDetailViewModel.metadataEventDetailUIState) {
                                at.torch.compose.ui.view.state.MetadataEventDetailUIState.Error -> {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = "Something went wrong"
                                    )
                                }
                                is at.torch.compose.ui.view.state.MetadataEventDetailUIState.Loaded -> {

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
                                at.torch.compose.ui.view.state.MetadataEventDetailUIState.Loading -> {
                                    LoadingIndicator()
                                }
                            }

                            IconButton(
                                onClick = {
                                    onNavigateToChatRoom.invoke(
                                        _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.ChatRoomDetailRoute(
                                            activeUserPublicKey = activeUserPublicKey,
                                            chatRoomId = localNostrEvent.nostrEvent.pubKey,
                                            relayHint = localNostrEvent.nostrEvent.relayUrl,
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
                        at.torch.compose.ui.view.model.MetadataEventDetailType.entries.forEachIndexed { index, destination ->
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
                            val metadataEventDetailType = at.torch.compose.ui.view.model.MetadataEventDetailType.entries[pageIndex]

                            when (metadataEventDetailType) {
                                at.torch.compose.ui.view.model.MetadataEventDetailType.Posts, at.torch.compose.ui.view.model.MetadataEventDetailType.Replies,
                                at.torch.compose.ui.view.model.MetadataEventDetailType.Articles, at.torch.compose.ui.view.model.MetadataEventDetailType.Zaps,
                                at.torch.compose.ui.view.model.MetadataEventDetailType.Photos, at.torch.compose.ui.view.model.MetadataEventDetailType.Shorts, at.torch.compose.ui.view.model.MetadataEventDetailType.Videos,
                                at.torch.compose.ui.view.model.MetadataEventDetailType.Bookmarks, at.torch.compose.ui.view.model.MetadataEventDetailType.Reports, at.torch.compose.ui.view.model.MetadataEventDetailType.Relays -> {
                                    val feedListViewModel: at.torch.compose.ui.view.model.FeedListViewModel = viewModel(
                                        key = metadataEventDetailType.name,
                                        factory = at.torch.compose.ui.view.model.FeedListViewModel.factory(
                                            initialFeedListUIState = at.torch.compose.ui.view.state.FeedListUIState.Loading,
                                            synchronizationFilter = metadataEventDetailViewModel.getSynchronizationFilter(metadataEventDetailType),
                                            nostrRepository = nostrRepository
                                        ),
                                    )

                                    key(feedListViewModel.feedListUIState) {
                                        feedListViewModel.RenderFeed(
                                            activeUserPublicKey = activeUserPublicKey,
                                            onNavigateToEvent = onNavigateToEvent
                                        )
                                    }

                                    key(true) {
                                        feedListViewModel.initiate()
                                    }
                                }
                                // TODO: Support gallery feeds...
                                at.torch.compose.ui.view.model.MetadataEventDetailType.Following -> {
                                    val followingListViewModel: at.torch.compose.ui.view.model.FollowingListViewModel = viewModel(
                                        key = metadataEventDetailType.name,
                                        factory = at.torch.compose.ui.view.model.FollowingListViewModel.factory(
                                            initialFeedListUIState = at.torch.compose.ui.view.state.FollowingListUIState.Loading,
                                            publicKey = localNostrEvent.nostrEvent.pubKey,
                                            synchronizationFilter = metadataEventDetailViewModel.getSynchronizationFilter(metadataEventDetailType),
                                            nostrRepository = nostrRepository
                                        ),
                                    )


                                    key(followingListViewModel.followingListUIState) {
                                        followingListViewModel.RenderFeed(
                                            activeUserPublicKey = activeUserPublicKey,
                                            onNavigateToEvent = onNavigateToEvent
                                        )
                                    }

                                    key(true) {
                                        followingListViewModel.initiate()
                                    }
                                }
                                at.torch.compose.ui.view.model.MetadataEventDetailType.Followers -> {
                                    val followersListViewModel: at.torch.compose.ui.view.model.FollowersListViewModel = viewModel(
                                        key = metadataEventDetailType.name,
                                        factory = at.torch.compose.ui.view.model.FollowersListViewModel.factory(
                                            initialFeedListUIState = at.torch.compose.ui.view.state.FollowersListUIState.Loading,
                                            publicKey = localNostrEvent.nostrEvent.pubKey,
                                            synchronizationFilter = metadataEventDetailViewModel.getSynchronizationFilter(metadataEventDetailType),
                                            nostrRepository = nostrRepository
                                        ),
                                    )


                                    key(followersListViewModel.followersListUIState) {
                                        followersListViewModel.RenderFeed(
                                            activeUserPublicKey = activeUserPublicKey,
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

    _root_ide_package_.at.torch.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            MetadataEventDetail(
                activeUserPublicKey = "",
                localNostrEvent = _root_ide_package_.at.torch.compose.database.model.intermdiate.LocalNostrEvent(
                    nostrEvent = _root_ide_package_.at.torch.compose.database.model.NostrEvent(
                        id = "eventId",
                        pubKey = "publicKey",
                        content = """Profile""".trimIndent(),
                        tags = emptyArray(),
                        sig = "",
                        kind = MetadataEvent.KIND
                    ),
                    profile = _root_ide_package_.at.torch.compose.database.model.Profile(
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
                nostrRepository = at.torch.compose.repository.NostrRepository.NO_OP_NOSTR_REPOSITORY
            )
        }
    }
}