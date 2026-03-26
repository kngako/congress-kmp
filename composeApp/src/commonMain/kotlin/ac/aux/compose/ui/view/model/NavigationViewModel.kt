package ac.aux.compose.ui.view.model

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.managers.SeedManager
import ac.aux.compose.nostr.Relays
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.view.state.NavigationUIState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlin.time.Instant

class NavigationViewModel(
    initialNavigationUIState: NavigationUIState,
    val nostrRepository: NostrRepository
): ViewModel() {
    companion object {
        private const val TAG = "NavigationViewModel"

        fun factory(
            initialNavigationUIState: NavigationUIState,
            nostrRepository: NostrRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NavigationViewModel(
                    initialNavigationUIState = initialNavigationUIState,
                    nostrRepository = nostrRepository
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    private val _navigationUIState = MutableStateFlow(
        initialNavigationUIState
    )
    val navigationUIState = _navigationUIState.asStateFlow()

    init {
        observeUnsignedNostrEvents()
        observeProfile()
    }




    private fun observeUnsignedNostrEvents() {
        val tempSigner = NostrSignerSync(
            SeedManager.activeKeyPair()
        )
        logger.i { "observeUnsignedNostrEvents" }
        viewModelScope.launch {
            nostrRepository.observeUnsignedNostrEvents(
                publicKey = SeedManager.activePublicKey().toHexKey()
            ).collect { unsignedNostrEventOrNull ->
                unsignedNostrEventOrNull?.let { unsignedNostrEvent ->
                    val event = tempSigner.signNormal<Event>(
                        createdAt = unsignedNostrEvent.createdAt.toEpochMilliseconds(),
                        kind = unsignedNostrEvent.kind,
                        tags = unsignedNostrEvent.tags,
                        content = unsignedNostrEvent.content
                    )

                    nostrRepository.publishNostrEvent(
                        NostrEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            kind = event.kind,
                            tags = event.tags,
                            content = event.content,
                            createdAt = Instant.fromEpochMilliseconds(event.createdAt),
                            sig = event.sig,
                            unsignedNostrEventId = unsignedNostrEvent.id
                        ),
                        relayURLs = Relays.eventFinderRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
                    )
                }

            }
        }
    }
    private fun observeProfile() {
        logger.i("observeProfile")
        viewModelScope.launch {
            delay(2_100) // Looking busy...

            logger.i("Navigation UI State is Landing")
            val publicKey = SeedManager.activePublicKey().toHexKey()
            logger.i("Observing: $publicKey")
            nostrRepository.observeProfile(
                publicKey = publicKey
            ).collect { localProfile ->
                logger.i("Local Profile: $localProfile")
                _navigationUIState.getAndUpdate {
                    if (localProfile == null) {
                        logger.i("We need to be on the landing screen so user creates account")
                        NavigationUIState.Landing
                    } else if (localProfile.unsignedNostrEvent == null) {
                        logger.i("We need to be on the landing screen so user creates account")
                        NavigationUIState.Landing
                    } else if (localProfile.broadcastNostrEventReceipt != null) {
                        logger.i("We have a broadcast receipt: ${localProfile.profile}")
                        NavigationUIState.ProfileLoaded(
                            broadcastNostrEventReceipt = localProfile.broadcastNostrEventReceipt
                        )
                    } else if (localProfile.broadcastNostrEventRequest != null) {
                        logger.i("We have a profile: ${localProfile.profile}")
                        NavigationUIState.UnannouncedProfile(
                            broadcastNostrEventRequest = localProfile.broadcastNostrEventRequest
                        )
                    }  else if (localProfile.profile != null) {
                        logger.i("We have a profile that needs to queued for broadcast: ${localProfile.profile}")
                        NavigationUIState.UnqueuedProfile(
                            profile = localProfile.profile
                        )
                    } else if (localProfile.nostrEvent != null) {
                        logger.i("Nostr event with the profile needs to be indexed: ${localProfile.nostrEvent}")
                        NavigationUIState.UnindexedProfile(
                            nostrEvent = localProfile.nostrEvent
                        )
                    } else {
                        logger.i("We have an unsigned profile: ${localProfile.unsignedNostrEvent}")
                        NavigationUIState.UnsignedProfile(
                            unsignedNostrEvent = localProfile.unsignedNostrEvent
                        )
                    }
                }
            }
        }
    }

}