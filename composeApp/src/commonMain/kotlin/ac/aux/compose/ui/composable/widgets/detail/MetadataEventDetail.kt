package ac.aux.compose.ui.composable.widgets.detail

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.intermdiate.LocalNostrEvent
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.composable.navigation.routes.NostrEventDetailRoute
import ac.aux.compose.ui.composable.navigation.routes.Route
import ac.aux.compose.ui.composable.widgets.feed.ListView
import ac.aux.compose.ui.theme.AuxTheme
import ac.aux.compose.ui.view.model.MetadataEventDetailType
import ac.aux.compose.ui.view.model.MetadataEventDetailViewModel
import ac.aux.compose.ui.view.model.SearchResultType
import ac.aux.compose.ui.view.state.FeedListUIState
import ac.aux.compose.ui.view.state.SearchResultListUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    nostrRepository: NostrRepository
) {
    val scope = rememberCoroutineScope()

    val profile = localNostrEvent.profile
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val metadataEventDetailViewModel: MetadataEventDetailViewModel = viewModel(
        factory = MetadataEventDetailViewModel.factory(
            initialMetadataEventDetailType = MetadataEventDetailType.Posts,
            publicKey = localNostrEvent.nostrEvent.pubKey,
            nostrRepository = nostrRepository,
            pagerState = rememberPagerState { SearchResultType.entries.size }
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

                            Icon(
                                Icons.Default.Person,
                                modifier = Modifier.size(50.dp),
                                contentDescription = profile?.displayName ?: profile?.userName ?: "Profile picture"
                            )


                            Spacer(
                                modifier = Modifier.weight(1f)
                            )


                            Button(
                                onClick = {

                                }
                            ) {
                                Text(
                                    "Follow"
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

                item {
                    PrimaryScrollableTabRow(
                        modifier = Modifier.padding(10.dp),
                        edgePadding = 10.dp,
                        selectedTabIndex = metadataEventDetailViewModel.pagerState.currentPage,
                    ) {
                        MetadataEventDetailType.entries.forEachIndexed { index, destination ->
                            Tab(
                                selected = metadataEventDetailViewModel.pagerState.currentPage == index,
                                onClick = {

                                    metadataEventDetailViewModel.updateMetadataEventDetailType(destination) {
                                        scope.launch {
                                            metadataEventDetailViewModel.pagerState.animateScrollToPage(destination.ordinal)
                                        }
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
                        modifier = Modifier.fillParentMaxSize()
                    ) {
                        HorizontalPager(
                            state = metadataEventDetailViewModel.pagerState,
                        ) { pageIndex ->

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                when (val feedListUIState = metadataEventDetailViewModel.feedListUIState) {
                                    FeedListUIState.Error -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Spacer(
                                                modifier = Modifier.height(50.dp)
                                            )
                                            Text(
                                                text = "Something went wrong",
                                            )
                                        }
                                    }
                                    is FeedListUIState.Loaded -> {
                                        if (feedListUIState.localNostrEvents.isEmpty()) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            )  {
                                                Spacer(
                                                    modifier = Modifier.height(50.dp)
                                                )
                                                Text(
                                                    text = "No events were found",
                                                )
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(
                                                    items = feedListUIState.localNostrEvents,
                                                    key = { localNostrEvent -> localNostrEvent.nostrEvent.id}
                                                ) { localNostrEvent ->
                                                    localNostrEvent.ListView(
                                                        onNavigateToEvent = { nostrEventId ->
                                                            onNavigateToEvent.invoke(
                                                                NostrEventDetailRoute(
                                                                    nostrEventId = nostrEventId
                                                                )
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                    }
                                    FeedListUIState.Loading -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        )  {
                                            Spacer(
                                                modifier = Modifier.height(50.dp)
                                            )
                                            Text(
                                                text = "Loading ${MetadataEventDetailType.entries[pageIndex].name.lowercase()}",
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(
                                                modifier = Modifier.height(50.dp)
                                            )

                                            LoadingIndicator()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LaunchedEffect(true) {
                        metadataEventDetailViewModel.updateMetadataEventDetailType(
                            MetadataEventDetailType.Posts
                        ) {

                        }
                    }

                }
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
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY
            )
        }
    }
}