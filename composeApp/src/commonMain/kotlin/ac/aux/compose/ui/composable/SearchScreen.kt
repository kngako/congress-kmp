package ac.aux.compose.ui.composable

import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.repository.SearchRepository
import ac.aux.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import ac.aux.compose.ui.composable.navigation.routes.NostrEventDetailRoute
import ac.aux.compose.ui.composable.navigation.routes.Route
import ac.aux.compose.ui.composable.navigation.routes.SearchResultRoute
import ac.aux.compose.ui.theme.AuxTheme
import ac.aux.compose.ui.view.model.FeedListViewModel
import ac.aux.compose.ui.view.model.SearchViewModel
import ac.aux.compose.ui.view.state.SearchUIState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.YoutubeSearchedFor
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun SearchScreen(
    initialSearchUIState: SearchUIState,
    nostrRepository: NostrRepository,
    searchRepository: SearchRepository,
    onNavigateToSearchResult: (Route) -> Unit,
    onNavigateToProfile: (Route) -> Unit
) {
    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.factory(
            initialSearchUIState = initialSearchUIState,
            nostrRepository = nostrRepository,
            searchRepository = searchRepository
        ),
    )
    val textFieldState = rememberTextFieldState()
    // Controls expansion state of the search bar
    var expanded by rememberSaveable { mutableStateOf(false) }

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
                            onQueryChange = {
                                textFieldState.edit { replace(0, length, it) }
                                searchViewModel.filterLocalProfiles(it)
                            },
                            onSearch = {
                                if (textFieldState.text.isBlank()) {
                                    expanded = false
                                }

                            },
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            placeholder = { Text("Search") },
                            leadingIcon = {
                                if (expanded) {
                                    IconButton(
                                        onClick = {
                                            expanded = false
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
                            }
                        )
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    windowInsets = TopAppBarDefaults.windowInsets
                ) {
                    if (textFieldState.text.isNotBlank()) {
                        // Display search results in a scrollable column
                        LazyColumn {
                            item {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            textFieldState.text.toString()
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = "Search"
                                        )
                                    },
                                    leadingContent = {
                                        IconButton(
                                            onClick = {
                                                onNavigateToSearchResult.invoke(
                                                    SearchResultRoute(
                                                        textFieldState.text.toString()
                                                    )
                                                )
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.TravelExplore,
                                                contentDescription = "Search globally"
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .clickable {
                                            onNavigateToSearchResult.invoke(
                                                SearchResultRoute(
                                                    textFieldState.text.toString()
                                                )
                                            )
                                        }
                                        .fillMaxWidth(),

                                )
                            }

                            item {
                                ListItem(
                                    headlineContent = { Text(textFieldState.text.toString()) },
                                    supportingContent = {
                                        Text(
                                            text = "Search hashtags"
                                        )
                                    },
                                    leadingContent = {
                                        IconButton(
                                            onClick = {
                                                onNavigateToSearchResult.invoke(
                                                    SearchResultRoute(
                                                        query = "#${textFieldState.text.toString().replace("#","")}"
                                                    )
                                                )
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Tag,
                                                contentDescription = "Search hashtags"
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .clickable {
                                            onNavigateToSearchResult.invoke(
                                                SearchResultRoute(
                                                    "#${textFieldState.text.toString().replaceFirst("#","")}"
                                                )
                                            )
                                        }
                                        .fillMaxWidth(),

                                    )
                            }
                            itemsIndexed(searchViewModel.filteredProfiles) { index, profile ->
                                ListItem(
                                    headlineContent = { Text(profile.humanReadableNameOrPubkey()) },
                                    supportingContent = {
                                        Text("Profile")
                                    },
                                    leadingContent = {
                                        IconButton(
                                            onClick = {
                                                onNavigateToProfile.invoke(
                                                    NostrEventDetailRoute(profile.nostrEventId)
                                                )
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = "Person"
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .clickable {
                                            onNavigateToProfile.invoke(
                                                NostrEventDetailRoute(profile.nostrEventId)
                                            )
                                        }
                                        .fillMaxWidth(),

                                )
                            }
                        }
                    } else {
                        LazyColumn {
                            // Display search results in a scrollable column
                            item {
                                searchViewModel.searchableProfiles.let { searchableProfiles ->
                                    if (searchableProfiles.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                            ) {
                                                Text(
                                                    text = "Profiles",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }

                                            LazyRow {
                                                items(searchViewModel.searchableProfiles) { profile ->
                                                    val profileName = profile.displayName ?: profile.userName ?: profile.nip05 ?: profile.nostrEventId
                                                    Column(
                                                        modifier = Modifier.padding(10.dp).width(70.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        IconButton(
                                                            modifier = Modifier
                                                                .size(IconButtonDefaults.mediumContainerSize()),
                                                            onClick = {
                                                                onNavigateToProfile.invoke(
                                                                    NostrEventDetailRoute(
                                                                        profile.nostrEventId
                                                                    )
                                                                )
                                                            }
                                                        ) {
                                                            Icon(
                                                                modifier = Modifier.size(70.dp),
                                                                imageVector = Icons.Default.Person,
                                                                contentDescription = profileName
                                                            )
                                                        }

                                                        Text(
                                                            text = profileName,
                                                            overflow = TextOverflow.Ellipsis,
                                                            maxLines = 1,
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }

                                                }

                                            }

                                        }

                                    }
                                }
                            }
                            item {
                                if (searchViewModel.recentSearches.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                    ) {
                                        Text(
                                            text = "Recents",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }
                            items(
                                items = searchViewModel.recentSearches,
                                key = { recentSearch -> recentSearch.query }
                            ) { recentSearch ->
                                ListItem(
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    ),
                                    headlineContent = { Text(recentSearch.query) },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.YoutubeSearchedFor,
                                            contentDescription = "Recent search"
                                        )
                                    },
                                    modifier = Modifier
                                        .clickable {
                                            onNavigateToSearchResult.invoke(
                                                SearchResultRoute(
                                                    query = recentSearch.query
                                                )
                                            )
                                        }
                                        .fillMaxWidth(),
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                searchViewModel.removeRecentSearch(recentSearch)
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove search term"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Doing this to have a way to know how much space the search bar is actually taking up.
                    TopAppBar(
                        title = {}
                    )
                    Text(
                        text = "Trending notes functionality coming soon. In the meantime search for what you are looking for.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SearchScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            SearchScreen(
                initialSearchUIState = SearchUIState.Prompt,
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                searchRepository = SearchRepository.NO_OP_SEARCH_REPOSITORY,
                onNavigateToSearchResult = {},
                onNavigateToProfile = {}
            )
        }
    }
}