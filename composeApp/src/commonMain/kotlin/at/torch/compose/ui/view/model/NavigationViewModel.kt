package at.torch.compose.ui.view.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import at.torch.compose.database.model.intermdiate.LocalAccount
import at.torch.compose.ui.composable.navigation.routes.SovereignWalletStartupRoute
import at.torch.compose.ui.view.state.NavigationUIState
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
import kotlin.time.Duration.Companion.milliseconds

class NavigationViewModel(
    val activeWalletStateFlow: StateFlow<ActiveWallet?>,
    initialNavigationUIState: NavigationUIState,
    val nostrRepository: at.torch.compose.repository.NostrRepository,
    val scope: CoroutineScope,
): ViewModel() {

    companion object {
        private const val TAG = "NavigationViewModel"

        fun factory(
            activeWallet: StateFlow<ActiveWallet?>,
            initialNavigationUIState: NavigationUIState,
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

    suspend fun loadNostrProfile(startupRoute: SovereignWalletStartupRoute? = null) {
        val activeUserPublicKey = nostrRepository.getLocalAccounts().firstOrNull()?.profile?.publicKey

        if (activeUserPublicKey == null) {
            // TODO: We can just go directly to startup witout fear...
            _navigationUIState.getAndUpdate {
                if (startupRoute != null) {
                    NavigationUIState.Landing
                } else {
                    NavigationUIState.StartupPhoenix
                }
            }
        } else {
//            if (startupRoute != null) {
//                val localAccount = nostrRepository.observeProfile(
//                    publicKey = activeUserPublicKey
//                ).firstOrNull()
//
//                processLocalAccount(localAccount)
//
//            } else {
//                _navigationUIState.getAndUpdate {
//                    NavigationUIState.StartupPhoenix
//                }
//            }
        }
    }


    private fun processLocalAccount(localAccount: LocalAccount?) {
        logger.i("processLocalAccount: $localAccount")

        _navigationUIState.getAndUpdate {
            if (localAccount == null) {
                logger.i("We need to be on the landing screen so user creates account")
                NavigationUIState.Landing
            } else if (localAccount.unsignedNostrEvent == null) {
                logger.i("We need to be on the landing screen so user creates account")
                NavigationUIState.Landing
            } else if (localAccount.broadcastNostrEventReceipt != null) {
                logger.i("We have a broadcast receipt: ${localAccount.profile}")
                NavigationUIState.ProfileLoaded(
                    publicKey = localAccount.unsignedNostrEvent.pubKey
                )
            } else if (localAccount.unsignedNostrEvent.signedAt != null && localAccount.profile != null) {
                logger.i("We have successfully synced a profile: ${localAccount.profile}")
                NavigationUIState.ProfileLoaded(
                    publicKey = localAccount.unsignedNostrEvent.pubKey
                )
            } else if (localAccount.broadcastNostrEventRequest != null) {
                logger.i("We have a broadcast request: ${localAccount.broadcastNostrEventRequest}")
                NavigationUIState.UnannouncedProfile(
                    broadcastNostrEventRequest = localAccount.broadcastNostrEventRequest
                )
            }  else if (localAccount.profile != null) {
                logger.i("We have a profile that needs to queued for broadcast: ${localAccount.profile}")
                NavigationUIState.UnqueuedProfile(
                    profile = localAccount.profile
                )
            } else if (localAccount.nostrEvent != null) {
                logger.i("Nostr event with the profile needs to be indexed: ${localAccount.nostrEvent}")
                NavigationUIState.UnindexedProfile(
                    nostrEvent = localAccount.nostrEvent
                )
            } else if (localAccount.unsignedNostrEvent.signedAt != null && localAccount.synchronizeNostrEventRequests.isEmpty()) {
                logger.i("We should request to sync profile: ${localAccount.unsignedNostrEvent}")
                NavigationUIState.UnqueuedProfileSynchronization(
                    unsignedNostrEvent = localAccount.unsignedNostrEvent
                )
            }  else if (localAccount.unsignedNostrEvent.signedAt != null && localAccount.synchronizeNostrEventRequests.isEmpty()) {
                logger.i("We should be syncing the profile: ${localAccount.unsignedNostrEvent}")
                NavigationUIState.UnsyncedProfile(
                    unsignedNostrEvent = localAccount.unsignedNostrEvent
                )
            } else {
                logger.i("We have an unsigned profile: ${localAccount.unsignedNostrEvent}")
                NavigationUIState.UnsignedProfile(
                    unsignedNostrEvent = localAccount.unsignedNostrEvent
                )
            }
        }
    }


    private suspend fun loadNostrProfile(activeUserPublicKey: String) {
        nostrRepository.observeLocalAccount(
            publicKey = activeUserPublicKey
        ).distinctUntilChanged().collect { localProfile ->
            processLocalAccount(localProfile)
        }
    }

    private fun observeProfile() {
        logger.i("observeProfile")
        scope.launch(Dispatchers.IO) {
            delay(2_100.milliseconds) // Looking busy...

            activeWalletStateFlow.collectLatest { activeWallet ->
                logger.d("Active Wallet: $activeWallet")
                if (activeWallet ==  null) {

                    _navigationUIState.getAndUpdate {
                        NavigationUIState.StartupPhoenix
                    }
                } else {
                    if (activeWallet.business == null) {
                        _navigationUIState.getAndUpdate {
                            NavigationUIState.StartupPhoenix
                        }
                    } else {
                        val activeUserPublicKey = activeWallet.business.walletManager.keyManager.value?.nostrPublicKey()

                        if (activeUserPublicKey == null) {
                            _navigationUIState.getAndUpdate {
                                NavigationUIState.Loading(text = "Couldn't get the nostrKey")
                            }
                        } else {
                            logger.i("Observing: $activeUserPublicKey")

                            loadNostrProfile(activeUserPublicKey)
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