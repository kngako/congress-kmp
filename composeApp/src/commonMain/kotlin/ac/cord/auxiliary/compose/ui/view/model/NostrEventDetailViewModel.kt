package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.nostr.Relays
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.view.state.NostrEventDetailUIState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NostrEventDetailViewModel(
    val activeUserPublicKey: HexKey,
    val nostrEventId: HexKey,
    initialNostrEventDetailUIState: NostrEventDetailUIState,
    val nostrRepository: NostrRepository
): ViewModel() {
    var nostrEventDetailUIState: NostrEventDetailUIState by mutableStateOf(initialNostrEventDetailUIState)
        private set

    fun retryLoad() {
        nostrEventDetailUIState = NostrEventDetailUIState.Loading
        loadNostrFeed()
    }

    fun loadNostrFeed() {
        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            nostrRepository.queueSynchronizeNostrEvent(
                Relays.eventPublishRelaySet.take(1).map { normalizedRelay -> // TODO: Sync from all relays instead of 1
                    SynchronizeNostrEventRequest(
                        purpose = "detail",
                        synchronizationFilters = arrayOf(
                            SynchronizationFilter(
                                kinds = arrayOf(
                                    TextNoteEvent.KIND,
                                    RepostEvent.KIND,
                                    ReactionEvent.KIND,
                                    LnZapEvent.KIND,
                                ),
                                tags = mapOf(
                                    Pair(
                                        "p",
                                        listOf(
                                            activeUserPublicKey
                                        )
                                    )
                                ),
                                limit = 50
                            )
                        ),
                        relayURL = normalizedRelay.url,
                        level = 0
                    )
                }
            )

            // Get contact list and sync feed...
            nostrRepository.observeLocalNostrEventById(
                nostrEventId
            ).collectLatest { localNostrEvent ->
                nostrEventDetailUIState = if (localNostrEvent != null) {
                    NostrEventDetailUIState.Loaded(
                        localNostrEvent = localNostrEvent
                    )
                } else {
                    NostrEventDetailUIState.Error
                }
            }
        }
    }

    companion object {
        const val TAG = "NostrEventDetailViewModel"

        fun factory(
            nostrEventId: HexKey,
            activeUserPublicKey: HexKey,
            initialNostrEventDetailUIState: NostrEventDetailUIState = NostrEventDetailUIState.Loading,
            nostrRepository: NostrRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NostrEventDetailViewModel(
                    nostrEventId = nostrEventId,
                    initialNostrEventDetailUIState = initialNostrEventDetailUIState,
                    nostrRepository = nostrRepository,
                    activeUserPublicKey = activeUserPublicKey
                )
            }
        }
    }
}