package ac.aux.compose.ui.view.model

import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.RecentSearch
import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.types.SynchronizationFilter
import ac.aux.compose.nostr.Relays
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.repository.SearchRepository
import ac.aux.compose.ui.view.state.SearchUIState
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
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class SearchViewModel(
    initialSearchUIState: SearchUIState,
    val nostrRepository: NostrRepository,
    val searchRepository: SearchRepository,
): ViewModel() {
    val logger = Logger.withTag(TAG)

    var searchUIState: SearchUIState by mutableStateOf(initialSearchUIState)
        private set

    var searchableProfiles = mutableStateListOf<Profile>()
        private set

    var recentSearches = mutableStateListOf<RecentSearch>()
        private set

    init {
        loadSearchableProfiles()
        loadRecentSearches()
    }

    fun performSearch(searchQuery: String) {
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.saveSearchQuery(
                searchQuery,
                // TODO: synchronizationFilterArray
            )
            // TODO: Run search on local db for suggestions...
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

    fun removeRecentSearch(recentSearch: RecentSearch) {
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.removeRecentSearch(recentSearch)
            recentSearches.remove(recentSearch)
        }
    }

    companion object {
        const val TAG = "SearchViewModel"

        fun factory(
            initialSearchUIState: SearchUIState = SearchUIState.Prompt,
            nostrRepository: NostrRepository,
            searchRepository: SearchRepository,
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