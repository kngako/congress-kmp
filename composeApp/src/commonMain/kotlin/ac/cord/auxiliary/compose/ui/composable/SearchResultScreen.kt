package ac.cord.auxiliary.compose.ui.composable

import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.repository.SearchRepository
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.NostrEventDetailRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.Route
import ac.cord.auxiliary.compose.ui.composable.widgets.feed.ListView
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.view.model.SearchResultType
import ac.cord.auxiliary.compose.ui.view.model.SearchResultViewModel
import ac.cord.auxiliary.compose.ui.view.state.SearchResultListUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchResultScreen(
    activeUserPublicKey: String,
    searchQuery: String,
    nostrRepository: NostrRepository,
    searchRepository: SearchRepository,
    onNavigateBack: () -> Unit,
    onNavigateToEvent: (Route) -> Unit
) {
    val scope = rememberCoroutineScope()

    val searchViewModel: SearchResultViewModel = viewModel(
        factory = SearchResultViewModel.factory(
            initialSearchResultType = SearchResultType.Posts,
            searchQuery = searchQuery,
            nostrRepository = nostrRepository,
            searchRepository = searchRepository
        ),
    )

    val textFieldState = rememberTextFieldState(searchQuery)
    // Controls expansion state of the search bar
    var expanded by rememberSaveable { mutableStateOf(true) }


    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { isTraversalGroup = true }
            ) {

                SearchBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .semantics { traversalIndex = 0f },
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = textFieldState.text.toString(),
                            onQueryChange = { textFieldState.edit { replace(0, length, it) } },
                            onSearch = {
                                // Since search bar is disabled we don't need to do anything...
                            },
                            enabled = false,
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            placeholder = { Text("Search") },
                            leadingIcon = {
                                if (expanded) {
                                    IconButton(
                                        enabled = true,
                                        colors = IconButtonDefaults.iconButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        onClick = {
                                            // TODO: onNavigateBack
                                            onNavigateBack.invoke()
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowBackIosNew,
                                            contentDescription = "Back"
                                        )
                                    }

                                } else {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Search"
                                    )
                                }
                            },
                            trailingIcon = {
                                IconButton(
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    onClick = {
                                        // TODO: Adjust search
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = "Adjust Search"
                                    )
                                }
                            }
                        )
                    },
                    expanded = expanded,
                    onExpandedChange = {
                        onNavigateBack.invoke()
                    },
                    windowInsets = TopAppBarDefaults.windowInsets
                ) {
                    val pagerState = rememberPagerState { SearchResultType.entries.size }

                    PrimaryScrollableTabRow(
                        modifier = Modifier,
                        edgePadding = 10.dp,
                        selectedTabIndex = searchViewModel.searchResultType.ordinal,
                    ) {
                        SearchResultType.entries.forEach { searchResultType ->
                            Tab(
                                selected = searchViewModel.searchResultType == searchResultType,
                                onClick = {
                                    searchViewModel.updateSearchResultType(searchResultType)
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            searchResultType.ordinal
                                        )
                                    }
                                },
                                text = {
                                    Text(
                                        text = searchResultType.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }




                    HorizontalPager(
                        state = pagerState
                    ) { pageIndex ->

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when(SearchResultType.entries[pageIndex]) {
                                SearchResultType.Posts -> {
                                    Text("Posts")
                                }
                                SearchResultType.Articles -> {
                                    Text("Articles")
                                }
                                SearchResultType.Profiles -> {
                                    Text("Profiles")
                                }
                                SearchResultType.Photos -> {
                                    Text("Photos")
                                }
                                SearchResultType.Videos -> {
                                    Text("Videos")
                                }
                                SearchResultType.Shorts -> {
                                    Text("Shorts")
                                }
                            }

                            when (val searchResultListUIState = searchViewModel.searchResultListUIState) {
                                SearchResultListUIState.Error -> {
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
                                is SearchResultListUIState.Loaded -> {
                                    // TODO: Handle gallery UI
                                    if (searchResultListUIState.localNostrEvents.isEmpty()) {
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
                                                items = searchResultListUIState.localNostrEvents,
                                                key = { localNostrEvent -> localNostrEvent.nostrEvent.id}
                                            ) { localNostrEvent ->
                                                localNostrEvent.ListView(
                                                    onNavigateToEvent = { nostrEventId ->
                                                        onNavigateToEvent.invoke(
                                                            NostrEventDetailRoute(
                                                                activeUserPublicKey = activeUserPublicKey,
                                                                nostrEventId = nostrEventId
                                                            )
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }

                                }
                                SearchResultListUIState.Loading -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    )  {
                                        Spacer(
                                            modifier = Modifier.height(50.dp)
                                        )
                                        Text(
                                            text = "Searching for ${searchViewModel.searchResultType.name.lowercase()} on \"${searchQuery.lowercase()}\"",
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
            }
        }
    }

    LaunchedEffect(true) {
        searchViewModel.initiateSearchResults()
    }
}

@Preview
@Composable
private fun SearchResultScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            SearchResultScreen(
                activeUserPublicKey = "",
                searchQuery = "What happened...",
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                searchRepository = SearchRepository.NO_OP_SEARCH_REPOSITORY,
                onNavigateBack = {},
                onNavigateToEvent = {}
            )
        }
    }
}