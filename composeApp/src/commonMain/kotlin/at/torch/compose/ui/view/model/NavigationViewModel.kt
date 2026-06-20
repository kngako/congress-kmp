package at.torch.compose.ui.view.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import fr.acinq.phoenix.data.ActiveWallet
import fr.acinq.phoenix.managers.nostrPublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

class NavigationViewModel(
    val activeWalletStateFlow: StateFlow<ActiveWallet?>,
    initialNavigationUIState: at.torch.compose.ui.view.state.NavigationUIState,
    val nostrRepository: at.torch.compose.repository.NostrRepository,
    val scope: CoroutineScope,
): ViewModel() {

    companion object {
        private const val TAG = "NavigationViewModel"

        fun factory(
            activeWallet: StateFlow<ActiveWallet?>,
            initialNavigationUIState: at.torch.compose.ui.view.state.NavigationUIState,
            nostrRepository: at.torch.compose.repository.NostrRepository,
            scope: CoroutineScope,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NavigationViewModel(
                    activeWallet,
                    initialNavigationUIState = initialNavigationUIState,
                    nostrRepository = nostrRepository,
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

            activeWalletStateFlow.collectLatest { activeWallet ->
                logger.d("Active Wallet: $activeWallet")
                if (activeWallet ==  null) {
                    _navigationUIState.getAndUpdate {
                        at.torch.compose.ui.view.state.NavigationUIState.StartupPhoenix
                    }
                } else {
                    if (activeWallet.business == null) {
                        _navigationUIState.getAndUpdate {
                            at.torch.compose.ui.view.state.NavigationUIState.StartupPhoenix
                        }
                    } else {
                        val activeUserPublicKey = activeWallet.business.walletManager.keyManager.value?.nostrPublicKey()

                        if (activeUserPublicKey == null) {
                            _navigationUIState.getAndUpdate {
                                at.torch.compose.ui.view.state.NavigationUIState.Loading(text = "Couldn't get the nostrKey")
                            }
                        } else {

                            logger.i("Observing: $activeUserPublicKey")

                            nostrRepository.observeProfile(
                                publicKey = activeUserPublicKey
                            ).distinctUntilChanged().collect { localProfile ->
                                logger.i("Local Profile: $localProfile")
                                _navigationUIState.getAndUpdate {
                                    if (localProfile == null) {
                                        logger.i("We need to be on the landing screen so user creates account")
                                        at.torch.compose.ui.view.state.NavigationUIState.Landing
                                    } else if (localProfile.unsignedNostrEvent == null) {
                                        logger.i("We need to be on the landing screen so user creates account")
                                        at.torch.compose.ui.view.state.NavigationUIState.Landing
                                    } else if (localProfile.broadcastNostrEventReceipt != null) {
                                        logger.i("We have a broadcast receipt: ${localProfile.profile}")
                                        at.torch.compose.ui.view.state.NavigationUIState.ProfileLoaded(
                                            publicKey = localProfile.unsignedNostrEvent.pubKey
                                        )
                                    } else if (localProfile.unsignedNostrEvent.signedAt != null && localProfile.profile != null) {
                                        logger.i("We have successfully synced a profile: ${localProfile.profile}")
                                        at.torch.compose.ui.view.state.NavigationUIState.ProfileLoaded(
                                            publicKey = localProfile.unsignedNostrEvent.pubKey
                                        )
                                    } else if (localProfile.broadcastNostrEventRequest != null) {
                                        logger.i("We have a broadcast request: ${localProfile.broadcastNostrEventRequest}")
                                        at.torch.compose.ui.view.state.NavigationUIState.UnannouncedProfile(
                                            broadcastNostrEventRequest = localProfile.broadcastNostrEventRequest
                                        )
                                    }  else if (localProfile.profile != null) {
                                        logger.i("We have a profile that needs to queued for broadcast: ${localProfile.profile}")
                                        at.torch.compose.ui.view.state.NavigationUIState.UnqueuedProfile(
                                            profile = localProfile.profile
                                        )
                                    } else if (localProfile.nostrEvent != null) {
                                        logger.i("Nostr event with the profile needs to be indexed: ${localProfile.nostrEvent}")
                                        at.torch.compose.ui.view.state.NavigationUIState.UnindexedProfile(
                                            nostrEvent = localProfile.nostrEvent
                                        )
                                    } else if (localProfile.unsignedNostrEvent.signedAt != null && localProfile.synchronizeNostrEventRequests.isEmpty()) {
                                        logger.i("We should request to sync profile: ${localProfile.unsignedNostrEvent}")
                                        at.torch.compose.ui.view.state.NavigationUIState.UnqueuedProfileSynchronization(
                                            unsignedNostrEvent = localProfile.unsignedNostrEvent
                                        )
                                    }  else if (localProfile.unsignedNostrEvent.signedAt != null && localProfile.synchronizeNostrEventRequests.isEmpty()) {
                                        logger.i("We should be syncing the profile: ${localProfile.unsignedNostrEvent}")
                                        at.torch.compose.ui.view.state.NavigationUIState.UnsyncedProfile(
                                            unsignedNostrEvent = localProfile.unsignedNostrEvent
                                        )
                                    } else {
                                        logger.i("We have an unsigned profile: ${localProfile.unsignedNostrEvent}")
                                        at.torch.compose.ui.view.state.NavigationUIState.UnsignedProfile(
                                            unsignedNostrEvent = localProfile.unsignedNostrEvent
                                        )
                                    }
                                }
                            }
                        }
                    }
                    logger.i("Navigation UI State is Landing")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        logger.i("AppViewModel cleared")
    }

}