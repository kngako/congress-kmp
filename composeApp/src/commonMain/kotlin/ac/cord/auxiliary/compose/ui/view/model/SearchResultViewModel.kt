package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.nostr.Relays
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.repository.SearchRepository
import ac.cord.auxiliary.compose.ui.view.state.SearchResultListUIState
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
    Posts, Articles, Profiles, Photos, Shorts, Videos
}

class SearchResultViewModel(
    initialSearchResultType: SearchResultType,
    val searchQuery: String,
    val nostrRepository: NostrRepository,
    val searchRepository: SearchRepository,
): ViewModel() {
    val logger = Logger.withTag(TAG)

    var searchResultType: SearchResultType by mutableStateOf(initialSearchResultType)
        private set

    var searchResultListUIState: SearchResultListUIState by mutableStateOf(SearchResultListUIState.Loading)
        private set

    fun updateSearchResultType (searchResultType: SearchResultType) {
        searchResultListUIState = SearchResultListUIState.Loading

        this.searchResultType = searchResultType

        initiateSearchResults(searchResultType)
    }

    fun initiateSearchResults(searchResultType: SearchResultType = this.searchResultType) {
        logger.d("initiateSearchResults")
        val synchronizationFilter = when (searchResultType) {
            SearchResultType.Posts -> {
                SynchronizationFilter(
                    kinds = arrayOf(
                        TextNoteEvent.KIND,
                        RepostEvent.KIND,
                        ReactionEvent.KIND,
                        LnZapEvent.KIND,
                    ),
                    search = searchQuery,
                    limit = 50
                )
            }
            SearchResultType.Profiles -> {
                SynchronizationFilter(
                    kinds = arrayOf(
                        MetadataEvent.KIND,
                    ),
                    search = searchQuery,
                    limit = 50
                )
            }
            SearchResultType.Articles -> {
                SynchronizationFilter(
                    kinds = arrayOf(
                        LongTextNoteEvent.KIND,
                    ),
                    search = searchQuery,
                    limit = 50
                )
            }
            SearchResultType.Photos -> {
                SynchronizationFilter(
                    kinds = arrayOf(
                        PictureEvent.KIND,
                    ),
                    // TODO: filter for tags of pictures...
                    search = searchQuery,
                    limit = 50
                )
            }
            SearchResultType.Videos -> {
                SynchronizationFilter(
                    kinds = arrayOf(
                        VideoNormalEvent.KIND,
                    ),
                    // TODO: filter for tags of videos...
                    search = searchQuery,
                    limit = 50
                )
            }
            SearchResultType.Shorts -> {
                SynchronizationFilter(
                    kinds = arrayOf(
                        VideoShortEvent.KIND,
                    ),
                    // TODO: filter for tags of videos...
                    search = searchQuery,
                    limit = 50
                )
            }
        }

        saveSearchQuery(synchronizationFilter)
        scheduleSearch(synchronizationFilter)
        loadSearchResultFeed(synchronizationFilter)
    }

    private fun saveSearchQuery(synchronizationFilter: SynchronizationFilter) {
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

    private fun scheduleSearch(synchronizationFilter: SynchronizationFilter) {
        logger.d("scheduleSearch: $searchQuery")
        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            searchRepository.submitSearch(
                Relays.eventFinderRelaySet.take(1).map { normalizedRelay -> // TODO: Search all relays instead of the first
                    SynchronizeNostrEventRequest(
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

    private fun loadSearchResultFeed(synchronizationFilter: SynchronizationFilter) {
        logger.d("loadSearchResultFeed: $searchQuery")
        viewModelScope.launch(Dispatchers.IO) {
            // This is something not taking into consideration the results we are actually looking for...
            nostrRepository.observeNostrFeed(synchronizationFilter).distinctUntilChanged().collect { localNostrEvents ->
                searchResultListUIState = SearchResultListUIState.Loaded(
                    localNostrEvents = localNostrEvents
                )
            }
        }
    }

    companion object {
        const val TAG = "SearchViewModel"

        fun factory(
            initialSearchResultType: SearchResultType = SearchResultType.Posts,
            searchQuery: String,
            nostrRepository: NostrRepository,
            searchRepository: SearchRepository,
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