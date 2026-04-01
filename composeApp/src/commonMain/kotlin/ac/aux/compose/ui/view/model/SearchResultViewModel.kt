package ac.aux.compose.ui.view.model

import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.types.SynchronizationFilter
import ac.aux.compose.nostr.Relays
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.repository.SearchRepository
import ac.aux.compose.ui.view.state.FeedListUIState
import ac.aux.compose.ui.view.state.SearchResultListUIState
import androidx.compose.runtime.getValue
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

enum class SearchResultType {
    Posts, Articles, Profiles, Photos, Videos
}

class SearchResultViewModel(
    initialSearchResultType: SearchResultType,
    val nostrRepository: NostrRepository,
    val searchRepository: SearchRepository,
): ViewModel() {
    val logger = Logger.withTag(TAG)

    var searchResultType: SearchResultType by mutableStateOf(initialSearchResultType)
        private set

    var searchResultListUIState: SearchResultListUIState by mutableStateOf(SearchResultListUIState.Loading)
        private set

    fun updateSearchResultType (searchResultType: SearchResultType) {
        this.searchResultType = searchResultType

        reloadSearchResult()
    }

    fun reloadSearchResult() {
        searchResultListUIState = SearchResultListUIState.Loading
    }

    fun loadSearchResult() {
        viewModelScope.launch(Dispatchers.IO) {
            // This is something not taking into consideration the results we are actually looking for...
            nostrRepository.observeNostrFeed().collect { localNostrEvents ->
                searchResultListUIState = SearchResultListUIState.Loaded(
                    localNostrEvents = localNostrEvents
                )
            }
        }
    }

    fun performSearch(searchQuery: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            searchRepository.submitSearch(
                Relays.eventPublishRelaySet.map { normalizedRelay ->
                    SynchronizeNostrEventRequest(
                        purpose = "search",
                        synchronizationFilters = arrayOf(
                            SynchronizationFilter(
                                kinds = arrayOf(
                                    TextNoteEvent.KIND,
                                    RepostEvent.KIND,
                                    ReactionEvent.KIND,
                                    LnZapEvent.KIND,
                                ),
                                search = "shoes",
                                limit = 50
                            )
                        ),
                        relayURL = normalizedRelay.url
                    )
                }
            )
        }
    }


    companion object {
        const val TAG = "SearchViewModel"

        fun factory(
            initialSearchResultType: SearchResultType = SearchResultType.Posts,
            nostrRepository: NostrRepository,
            searchRepository: SearchRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchResultViewModel(
                    initialSearchResultType = initialSearchResultType,
                    nostrRepository = nostrRepository,
                    searchRepository = searchRepository
                )
            }
        }
    }
}