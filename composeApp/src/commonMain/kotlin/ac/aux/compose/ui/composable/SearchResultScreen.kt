package ac.aux.compose.ui.composable

import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.repository.SearchRepository
import ac.aux.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import ac.aux.compose.ui.composable.navigation.routes.Route
import ac.aux.compose.ui.composable.widgets.feed.ListView
import ac.aux.compose.ui.theme.AuxTheme
import ac.aux.compose.ui.view.model.SearchResultType
import ac.aux.compose.ui.view.model.SearchResultViewModel
import ac.aux.compose.ui.view.state.SearchResultListUIState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchResultScreen(
    searchQuery: String,
    nostrRepository: NostrRepository,
    searchRepository: SearchRepository,
    onNavigateBack: () -> Unit,
    onNavigateToEvent: (Route) -> Unit
) {
    val searchViewModel: SearchResultViewModel = viewModel(
        factory = SearchResultViewModel.factory(
            initialSearchResultType = SearchResultType.Posts,
            nostrRepository = nostrRepository,
            searchRepository = searchRepository
        ),
    )

    val searchResults = listOf(
        "Apple",
        "Banna",
        "Carrot",
        "Durian",
        "Eggplant",
        "Fig",
        "Grape",
        "Honeydew",
        "Jackfruit",
        "Kiwi",
        "Lemon",
        "Mango",
        "Nectarine",
        "Orange",
        "Papaya",
        "Quince",
        "Raspberry",
        "Tangerine",
        "Ugli Fruit",
        "Vanilla Bean",
        "Watermelon",
        "Xigua",
        "Yellow Passion Fruit",
        "Zinfandel",
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
                                searchViewModel.performSearch(
                                    textFieldState.text.toString()
                                )
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

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        when (val searchResultListUIState = searchViewModel.searchResultListUIState) {
                            SearchResultListUIState.Error -> {
                                item {
                                    Spacer(
                                        modifier = Modifier.height(50.dp)
                                    )
                                    Text(
                                        text = "Something went wrong",
                                    )
                                }
                            }
                            is SearchResultListUIState.Loaded -> {
                                if (searchResultListUIState.localNostrEvents.isEmpty()) {
                                    item {
                                        Spacer(
                                            modifier = Modifier.height(50.dp)
                                        )
                                        Text(
                                            text = "No events were found",
                                        )
                                    }
                                } else {
                                    items(
                                        items = searchResultListUIState.localNostrEvents,
                                        key = {localNostrEvent -> localNostrEvent.nostrEvent.id}
                                    ) { localNostrEvent ->
                                        localNostrEvent.ListView(
                                            onNavigateToEvent = {
                                                onNavigateToEvent.invoke(
                                                    ImplementationPendingRoute("Navigating to detail")
                                                )
                                            }
                                        )
                                    }
                                }

                            }
                            SearchResultListUIState.Loading -> {
                                item {
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

@Preview
@Composable
private fun SearchResultScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            SearchResultScreen(
                searchQuery = "What happened...",
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                searchRepository = SearchRepository.NO_OP_SEARCH_REPOSITORY,
                onNavigateBack = {},
                onNavigateToEvent = {}
            )
        }
    }
}