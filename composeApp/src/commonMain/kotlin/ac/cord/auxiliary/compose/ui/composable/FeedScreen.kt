package ac.cord.auxiliary.compose.ui.composable

import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.managers.SeedManager
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.widgets.LoadingDataIndicator
import ac.cord.auxiliary.compose.ui.composable.widgets.feed.FeedListItem
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.view.model.FeedListViewModel
import ac.cord.auxiliary.compose.ui.view.state.FeedListUIState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedScreen(
    initialFeedListUIState: FeedListUIState,
    onNavigateToEvent: (HexKey) -> Unit,
    onNavigateToWriteNewNote: () -> Unit,
    onNavigateToSearch: () -> Unit,
    nostrRepository: NostrRepository,
) {
    val feedListViewModel: FeedListViewModel = viewModel(
        factory = FeedListViewModel.factory(
            initialFeedListUIState = initialFeedListUIState,
            synchronizationFilter = SynchronizationFilter(
                kinds = arrayOf(
                    TextNoteEvent.KIND,
                    RepostEvent.KIND,
                    ReactionEvent.KIND,
                    LnZapEvent.KIND,
                    ContactListEvent.KIND,
                    AdvertisedRelayListEvent.KIND,
                    PrivateDmEvent.KIND,
                    BlossomServersEvent.KIND,
                    FileServersEvent.KIND
                ),
                tags = mapOf(
                    Pair("p", listOf(SeedManager.activePublicKey().toHexKey()))
                ),
                limit = 50
            ),
            nostrRepository = nostrRepository
        ),
    )

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

                        }
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Account"
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
            when(val feedListUIState = feedListViewModel.feedListUIState) {
                is FeedListUIState.Loading -> {
                    LoadingDataIndicator()
                }
                FeedListUIState.Error -> {
                    Text("Something went wrong")
                }
                is FeedListUIState.Loaded -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        stickyHeader {
                            Surface() {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp)
                                        .horizontalScroll(
                                            rememberScrollState()
                                        ),

                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ){

                                    FilterChip(
                                        selected = true,
                                        onClick = {},
                                        label = {
                                            Text("All")
                                        }
                                    )

                                    FilterChip(
                                        selected = false,
                                        onClick = {},
                                        label = {
                                            Text("Kind")
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Author Filter"
                                            )
                                        }
                                    )

                                    FilterChip(
                                        selected = false,
                                        onClick = {},
                                        label = {
                                            Text("Author")
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Author Filter"
                                            )
                                        }
                                    )

                                    FilterChip(
                                        selected = false,
                                        onClick = {},
                                        label = {
                                            Text("Unread")
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Author Filter"
                                            )
                                        }
                                    )
                                }
                            }

                        }
                        if (feedListViewModel.isSynchronizationPending.value) {
                            item {
                                Spacer(
                                    modifier = Modifier.height(30.dp)
                                )
                                LoadingIndicator()
                            }
                        }
                        items(
                            items = feedListUIState.localNostrEvents,
                            key = { localNostrEvent -> localNostrEvent.nostrEvent.id }
                        ) { localNostrEvent ->
                            FeedListItem(
                                localNostrEvent = localNostrEvent,
                                onNavigateToEvent = onNavigateToEvent
                            )
                        }
                    }
                }
            }
        }

        key(true) {
            feedListViewModel.initiate()
        }
    }
}

@Preview
@Composable
private fun FeedScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            FeedScreen(
                initialFeedListUIState = FeedListUIState.Loaded(emptyList()),
                onNavigateToEvent = {},
                onNavigateToWriteNewNote = {},
                onNavigateToSearch = {},
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY
            )
        }
    }
}