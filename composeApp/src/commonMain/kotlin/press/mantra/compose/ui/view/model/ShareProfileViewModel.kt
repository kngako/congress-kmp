package press.mantra.compose.ui.view.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import press.mantra.compose.database.model.BroadcastNostrEventRequest
import press.mantra.compose.database.model.intermdiate.LocalNostrEvent
import press.mantra.compose.nostr.Relays
import press.mantra.compose.ui.view.state.ShareProfileUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ShareProfileViewModel(
    val activeUserPublicKey: HexKey,
    val nostrEventId: HexKey,
    initialActiveProfileUIState: ShareProfileUIState,
    val nostrRepository: press.mantra.compose.repository.NostrRepository
): ViewModel() {
    var shareProfileUIState: ShareProfileUIState by mutableStateOf(initialActiveProfileUIState)
        private set

    fun retryLoad() {
        shareProfileUIState = ShareProfileUIState.Loading
        loadNostrFeed()
    }

    fun loadNostrFeed() {
        viewModelScope.launch(Dispatchers.IO) {

            // Get contact list and sync feed...
            nostrRepository.observeLocalNostrEventById(
                nostrEventId
            ).firstOrNull().let { localNostrEvent ->
                shareProfileUIState = if (localNostrEvent != null && localNostrEvent.nostrEvent.kind == MetadataEvent.KIND) {
                    ShareProfileUIState.Loaded(
                        localNostrEvent = localNostrEvent
                    )
                } else {
                    ShareProfileUIState.Error
                }
            }
        }
    }

    fun rebroadcastProfile(localNostrEvent: LocalNostrEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            // Rebroadcast
            nostrRepository.rescheduleBroadcastNostrEventRequests(
                Relays.eventFinderRelaySet.map {
                    BroadcastNostrEventRequest(
                        nostrEventId = localNostrEvent.nostrEvent.id,
                        relayURL = it.url
                    )
                }
            )
        }
    }

    companion object {
        const val TAG = "ShareProfileViewModel"

        fun factory(
            nostrEventId: HexKey,
            activeUserPublicKey: HexKey,
            initialActiveProfileUIState: ShareProfileUIState = ShareProfileUIState.Loading,
            nostrRepository: press.mantra.compose.repository.NostrRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ShareProfileViewModel(
                    nostrEventId = nostrEventId,
                    initialActiveProfileUIState = initialActiveProfileUIState,
                    nostrRepository = nostrRepository,
                    activeUserPublicKey = activeUserPublicKey
                )
            }
        }
    }
}