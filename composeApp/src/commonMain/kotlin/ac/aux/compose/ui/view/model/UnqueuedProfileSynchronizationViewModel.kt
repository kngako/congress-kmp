package ac.aux.compose.ui.view.model

import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.types.SynchronizationFilter
import ac.aux.compose.nostr.Relays
import ac.aux.compose.repository.NostrRepository
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UnqueuedProfileSynchronizationViewModel(
    val unsignedNostrEventId: Long,
    val profilePublicKey: String,
    val nostrRepository: NostrRepository
): ViewModel() {
    companion object {
        private const val TAG = "UnqueuedProfileSynchronizationViewModel"

        fun factory(
            unsignedNostrEventId: Long,
            profilePublicKey: String,
            nostrRepository: NostrRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UnqueuedProfileSynchronizationViewModel(
                    unsignedNostrEventId = unsignedNostrEventId,
                    profilePublicKey = profilePublicKey,
                    nostrRepository = nostrRepository
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun queueSynchronization() {
        logger.d { "queueSynchronization" }
        viewModelScope.launch(Dispatchers.IO) {

            delay(2_000)

            nostrRepository.queueSynchronizeNostrEvent(
                Relays.eventPublishRelaySet.map { normalizedRelayUrl ->
                    SynchronizeNostrEventRequest(
                        synchronizationFilters = arrayOf(
                            SynchronizationFilter(
                                authors = arrayOf(profilePublicKey),
                                kinds = arrayOf(MetadataEvent.KIND)
                            )
                        ),
                        unsignedNostrEventId = unsignedNostrEventId,
                        relayURL = normalizedRelayUrl.url
                    )
                }

            )
        }

    }
}