package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.managers.SeedManager
import ac.cord.auxiliary.compose.network.relays.RelaysSocketManager
import ac.cord.auxiliary.compose.network.sockets.NostrSocketClientFactory
import ac.cord.auxiliary.compose.repository.CachingImportRepository
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.repository.RelayRepository
import ac.cord.auxiliary.compose.ui.view.state.NavigationUIState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class NavigationViewModel(
    initialNavigationUIState: NavigationUIState,
    val nostrRepository: NostrRepository,
    val relayRepository: RelayRepository,
    val scope: CoroutineScope,
): ViewModel() {

    val relaysSocketManager = RelaysSocketManager(
        nostrSocketClientFactory = NostrSocketClientFactory,
        cachingImportRepository = CachingImportRepository.NO_OP_CACHING_IMPORT_REPOSITORY,
        relayRepository = relayRepository,
    )

    companion object {
        private const val TAG = "NavigationViewModel"

        private val mutex = Mutex()

        fun factory(
            initialNavigationUIState: NavigationUIState,
            nostrRepository: NostrRepository,
            relayRepository: RelayRepository,
            scope: CoroutineScope,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NavigationViewModel(
                    initialNavigationUIState = initialNavigationUIState,
                    nostrRepository = nostrRepository,
                    relayRepository = relayRepository,
                    scope = scope
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
        observeProfile()
    }
    private fun observeProfile() {
        logger.i("observeProfile")
        scope.launch(Dispatchers.IO) {
            delay(2_100) // Looking busy...

            logger.i("Navigation UI State is Landing")
            val publicKey = SeedManager.activePublicKey().toHexKey()
            logger.i("Observing: $publicKey")
            nostrRepository.observeProfile(
                publicKey = publicKey
            ).distinctUntilChanged().collect { localProfile ->
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
                            publicKey = localProfile.unsignedNostrEvent.pubKey
                        )
                    } else if (localProfile.unsignedNostrEvent.signedAt != null && localProfile.profile != null) {
                        logger.i("We have successfully synced a profile: ${localProfile.profile}")
                        NavigationUIState.ProfileLoaded(
                            publicKey = localProfile.unsignedNostrEvent.pubKey
                        )
                    } else if (localProfile.broadcastNostrEventRequest != null) {
                        logger.i("We have a broadcast request: ${localProfile.broadcastNostrEventRequest}")
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
                    } else if (localProfile.unsignedNostrEvent.signedAt != null && localProfile.synchronizeNostrEventRequests.isEmpty()) {
                        logger.i("We should request to sync profile: ${localProfile.unsignedNostrEvent}")
                        NavigationUIState.UnqueuedProfileSynchronization(
                            unsignedNostrEvent = localProfile.unsignedNostrEvent
                        )
                    }  else if (localProfile.unsignedNostrEvent.signedAt != null && localProfile.synchronizeNostrEventRequests.isEmpty()) {
                        logger.i("We should be syncing the profile: ${localProfile.unsignedNostrEvent}")
                        NavigationUIState.UnsyncedProfile(
                            unsignedNostrEvent = localProfile.unsignedNostrEvent
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