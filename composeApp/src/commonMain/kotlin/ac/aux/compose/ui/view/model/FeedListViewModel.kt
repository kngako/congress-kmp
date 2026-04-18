package ac.aux.compose.ui.view.model

import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.types.SynchronizationFilter
import ac.aux.compose.managers.SeedManager
import ac.aux.compose.nostr.Relays
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.view.state.FeedListUIState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class FeedListViewModel(
    initialFeedListUIState: FeedListUIState,
    val nostrRepository: NostrRepository
): ViewModel() {
    val logger = Logger.withTag(TAG)
    var feedListUIState: FeedListUIState by mutableStateOf(initialFeedListUIState)
        private set

    val isSynchronizationPending: MutableState<Boolean> = mutableStateOf(false)

    init {
        logger.d("init")
        scheduleSynchronization()
        observeLocalNostrFeed()
        observeSynchronizeNostrEventRequestByPurposeAndStatusCount()
    }

    fun observeLocalNostrFeed() {
        logger.d("observeLocalNostrFeed")
        viewModelScope.launch(Dispatchers.IO) {
            // Get contact list and sync feed...

            val localProfile = nostrRepository.observeProfile(SeedManager.activePublicKey().toHexKey()).firstOrNull()

            if (localProfile?.profile == null) {
                feedListUIState = FeedListUIState.Error
            } else {
                nostrRepository.observeNostrFeed().collect { localNostrEvents ->
                    feedListUIState = FeedListUIState.Loaded(
                        localNostrEvents = localNostrEvents
                    )
                }

            }
        }
    }

    fun scheduleSynchronization() {
        logger.d("scheduleSynchronization")
        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            nostrRepository.queueSynchronizeNostrEvent(
                Relays.eventPublishRelaySet.map { normalizedRelay ->
                    SynchronizeNostrEventRequest(
                        purpose = "feed",
                        synchronizationFilters = arrayOf(
                            SynchronizationFilter(
                                kinds = arrayOf(
                                    TextNoteEvent.KIND,
                                    RepostEvent.KIND,
                                    ReactionEvent.KIND,
                                    LnZapEvent.KIND,
                                    ContactListEvent.KIND,
                                    AdvertisedRelayListEvent.KIND,
                                    PrivateDmEvent.KIND,
                                    BlossomServersEvent.KIND,
                                    FileServersEvent.KIND
                                ),
                                tags = mapOf(
                                    Pair("p", listOf(SeedManager.activePublicKey().toHexKey()))
                                ),
                                limit = 50
                            )
                        ),
                        relayURL = normalizedRelay.url
                    )
                }
            )
        }
    }

    fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount() {
        logger.d("observeSynchronizeNostrEventRequestByPurposeAndStatusCount")
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.observeSynchronizeNostrEventRequestByPurposeAndStatusCount("feed").collect { count ->
                logger.d("Pending/Processing Synchronization Request: $count")
                isSynchronizationPending.value = count > 0
            }
        }
    }

    companion object {
        const val TAG = "FeedListViewModel"

        fun factory(
            initialFeedListUIState: FeedListUIState = FeedListUIState.Loading,
            nostrRepository: NostrRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FeedListViewModel(
                    initialFeedListUIState = initialFeedListUIState,
                    nostrRepository = nostrRepository
                )
            }
        }
    }
}