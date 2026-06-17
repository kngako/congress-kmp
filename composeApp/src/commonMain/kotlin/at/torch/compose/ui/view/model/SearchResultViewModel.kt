package at.torch.compose.ui.view.model

import at.torch.compose.database.model.SynchronizeNostrEventRequest
import at.torch.compose.database.model.types.SynchronizationFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class SearchResultType {
    Profiles
}

class SearchResultViewModel(
    initialSearchResultType: SearchResultType,
    val searchQuery: String,
    val nostrRepository: at.torch.compose.repository.NostrRepository,
    val searchRepository: at.torch.compose.repository.SearchRepository,
): ViewModel() {
    val logger = Logger.withTag(TAG)

    var searchResultType: SearchResultType by mutableStateOf(initialSearchResultType)
        private set

    var searchResultListUIState: at.torch.compose.ui.view.state.SearchResultListUIState by mutableStateOf(
        _root_ide_package_.at.torch.compose.ui.view.state.SearchResultListUIState.Loading)
        private set

    fun updateSearchResultType (searchResultType: SearchResultType) {
        searchResultListUIState = _root_ide_package_.at.torch.compose.ui.view.state.SearchResultListUIState.Loading

        this.searchResultType = searchResultType

        initiateSearchResults(searchResultType)
    }

    fun initiateSearchResults(searchResultType: SearchResultType = this.searchResultType) {
        logger.d("initiateSearchResults")
        val synchronizationFilter = when (searchResultType) {
            SearchResultType.Profiles -> {
                _root_ide_package_.at.torch.compose.database.model.types.SynchronizationFilter(
                    kinds = arrayOf(
                        MetadataEvent.KIND,
                    ),
                    search = searchQuery,
                    limit = 50
                )
            }
        }

        saveSearchQuery(synchronizationFilter)
        scheduleSearch(synchronizationFilter)
        loadSearchResultFeed(synchronizationFilter)
    }

    private fun saveSearchQuery(synchronizationFilter: at.torch.compose.database.model.types.SynchronizationFilter) {
        logger.d("saveSearchQuery: $searchQuery")
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.saveSearchQuery(
                searchQuery,
                arrayOf(
                    synchronizationFilter
                )
            )
        }
    }

    private fun scheduleSearch(synchronizationFilter: at.torch.compose.database.model.types.SynchronizationFilter) {
        logger.d("scheduleSearch: $searchQuery")
        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            searchRepository.submitSearch(
                _root_ide_package_.at.torch.compose.nostr.Relays.eventFinderRelaySet.take(1).map { normalizedRelay -> // TODO: Search all relays instead of the first
                    _root_ide_package_.at.torch.compose.database.model.SynchronizeNostrEventRequest(
                        purpose = "search",
                        synchronizationFilters = arrayOf(
                            synchronizationFilter
                        ),
                        relayURL = normalizedRelay.url,
                        level = 0
                    )
                }
            )
        }
    }

    private fun loadSearchResultFeed(synchronizationFilter: at.torch.compose.database.model.types.SynchronizationFilter) {
        logger.d("loadSearchResultFeed: $searchQuery")
        viewModelScope.launch(Dispatchers.IO) {
            // This is something not taking into consideration the results we are actually looking for...
            nostrRepository.observeNostrFeed(synchronizationFilter).distinctUntilChanged().collect { localNostrEvents ->
                searchResultListUIState = _root_ide_package_.at.torch.compose.ui.view.state.SearchResultListUIState.Loaded(
                    localNostrEvents = localNostrEvents
                )
            }
        }
    }

    companion object {
        const val TAG = "SearchViewModel"

        fun factory(
            initialSearchResultType: SearchResultType = SearchResultType.Profiles,
            searchQuery: String,
            nostrRepository: at.torch.compose.repository.NostrRepository,
            searchRepository: at.torch.compose.repository.SearchRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchResultViewModel(
                    initialSearchResultType = initialSearchResultType,
                    searchQuery = searchQuery,
                    nostrRepository = nostrRepository,
                    searchRepository = searchRepository
                )
            }
        }
    }
}