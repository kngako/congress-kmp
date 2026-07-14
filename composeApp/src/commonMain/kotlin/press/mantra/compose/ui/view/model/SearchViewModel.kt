package press.mantra.compose.ui.view.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class SearchViewModel(
    initialSearchUIState: press.mantra.compose.ui.view.state.SearchUIState,
    val nostrRepository: press.mantra.compose.repository.NostrRepository,
    val searchRepository: press.mantra.compose.repository.SearchRepository,
): ViewModel() {
    val logger = Logger.withTag(TAG)

    var searchUIState: press.mantra.compose.ui.view.state.SearchUIState by mutableStateOf(initialSearchUIState)
        private set

    var searchableProfiles = mutableStateListOf<press.mantra.compose.database.model.Profile>()
        private set

    var filteredProfiles = mutableStateListOf<press.mantra.compose.database.model.Profile>()
        private set

    var recentSearches = mutableStateListOf<press.mantra.compose.database.model.RecentSearch>()
        private set

    init {
        loadSearchableProfiles()
        loadRecentSearches()
    }

    fun filterLocalProfiles(searchTerm: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val filteredLocalProfiles = nostrRepository.filterProfiles(searchTerm = searchTerm)

            filteredProfiles.clear()
            filteredProfiles.addAll(
                filteredLocalProfiles.map { it.profile }
            )
        }
    }

    fun loadSearchableProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            searchableProfiles.addAll(
                nostrRepository.searchableProfiles()
            )
        }
    }

    fun loadRecentSearches() {
        viewModelScope.launch(Dispatchers.IO) {
            recentSearches.addAll(
                nostrRepository.getRecentSearches()
            )
        }
    }

    fun removeRecentSearch(recentSearch: press.mantra.compose.database.model.RecentSearch) {
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.removeRecentSearch(recentSearch)
            recentSearches.remove(recentSearch)
        }
    }

    companion object {
        const val TAG = "SearchViewModel"

        fun factory(
            initialSearchUIState: press.mantra.compose.ui.view.state.SearchUIState = press.mantra.compose.ui.view.state.SearchUIState.Prompt,
            nostrRepository: press.mantra.compose.repository.NostrRepository,
            searchRepository: press.mantra.compose.repository.SearchRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    initialSearchUIState = initialSearchUIState,
                    nostrRepository = nostrRepository,
                    searchRepository = searchRepository
                )
            }
        }
    }
}