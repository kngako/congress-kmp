package ac.aux.compose.ui.view.model

import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.nostr.Relays
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.view.state.SignInToProfileUIState
import ac.aux.compose.ui.view.state.form.SignInToProfileFormState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class UnqueuedProfileSynchronizationViewModel(
    val profilePublicKey: String,
    val nostrRepository: NostrRepository
): ViewModel() {
    companion object {
        private const val TAG = "UnqueuedProfileSynchronizationViewModel"

        fun factory(
            profilePublicKey: String,
            nostrRepository: NostrRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UnqueuedProfileSynchronizationViewModel(
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

            nostrRepository.queueSynchronizeNostrEvent(
                Relays.eventPublishRelaySet.map { normalizedRelayUrl ->
                    SynchronizeNostrEventRequest(
                        authorPublicKeys = profilePublicKey,
                        kinds = "0",
                        relayURL = normalizedRelayUrl.url
                    )
                }

            )
        }

    }
}