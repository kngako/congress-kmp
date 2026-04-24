package ac.cord.auxiliary.compose.ui.composable

import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.Route
import ac.cord.auxiliary.compose.ui.composable.widgets.LoadingDataIndicator
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.view.model.FeedListViewModel
import ac.cord.auxiliary.compose.ui.view.model.HomeScreenType
import ac.cord.auxiliary.compose.ui.view.model.HomeViewModel
import ac.cord.auxiliary.compose.ui.view.state.FeedListUIState
import ac.cord.auxiliary.compose.ui.view.state.HomeScreenUIState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onNavigateToEvent: (Route) -> Unit,
    onNavigateToWriteNewNote: () -> Unit,
    onNavigateToSearch: () -> Unit,
    nostrRepository: NostrRepository,
) {
    val scope = rememberCoroutineScope()

    val homeScreenViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            nostrRepository = nostrRepository,
            pagerState = rememberPagerState { HomeScreenType.entries.size }
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
            when(val homeScreenUIState = homeScreenViewModel.homeScreenUIState) {
                HomeScreenUIState.Error -> {
                    Text("Something went wrong")
                }
                is HomeScreenUIState.Loaded -> {
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
                                    Text("Messaging coming soon.")
                                }
                            }
                        }
                    }
                }
                HomeScreenUIState.Loading -> {
                    LoadingDataIndicator()
                }
            }



        }

        key(true) {
            homeScreenViewModel.initiate()
        }
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
                onNavigateToEvent = {},
                onNavigateToWriteNewNote = {},
                onNavigateToSearch = {},
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY
            )
        }
    }
}