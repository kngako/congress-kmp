package ac.aux.compose.ui.view.model

import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.intermdiate.LocalProfile
import ac.aux.compose.database.model.types.SynchronizationFilter
import ac.aux.compose.managers.SeedManager
import ac.aux.compose.nostr.Relays
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.repository.SearchRepository
import ac.aux.compose.ui.view.state.SearchUIState
import androidx.compose.runtime.MutableState
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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
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

    init {
        loadSearchableProfiles()
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

    fun loadSearchableProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            searchableProfiles.addAll(
                nostrRepository.searchableProfiles()
            )
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