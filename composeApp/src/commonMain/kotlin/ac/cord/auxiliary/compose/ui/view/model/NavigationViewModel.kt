package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.extensions.getGlobalPrefs
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.widgets.wallet.WalletAvatars
import ac.cord.auxiliary.compose.ui.view.state.NavigationUIState
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
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import fr.acinq.lightning.logging.error
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.PhoenixGlobal
import fr.acinq.phoenix.data.ActiveWallet
import fr.acinq.phoenix.data.DecryptSeedResult
import fr.acinq.phoenix.data.ElectrumConfig
import fr.acinq.phoenix.data.ListWalletState
import fr.acinq.phoenix.data.UserWallet
import fr.acinq.phoenix.data.WalletId
import fr.acinq.phoenix.managers.DataStoreManager
import fr.acinq.phoenix.managers.WalletManager
import fr.acinq.phoenix.managers.nostrPrivateKey
import fr.acinq.phoenix.managers.nostrPublicKey
import fr.acinq.phoenix.utils.preferences.GlobalPrefs
import fr.acinq.phoenix.utils.preferences.UserWalletMetadata
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

sealed class WritingSeedState {
    data object Init : WritingSeedState()
    data class Writing(val mnemonics: List<String>) : WritingSeedState()
    data class WrittenToDisk(val walletId: WalletId) : WritingSeedState()
    sealed class Error : WritingSeedState() {
        data class Generic(val cause: Throwable) : Error()
        data object CannotLoadSeedMap: Error()
        data object SeedAlreadyExists: Error()
    }
}

expect fun updateBusinessActiveInUI(walletId: WalletId)
expect fun loadAndDecryptSeed(phoenixGlobal: PhoenixGlobal): DecryptSeedResult

expect fun getAvailableWalletsMeta(phoenixGlobal: PhoenixGlobal): Flow<Map<WalletId, UserWalletMetadata>>

expect suspend fun saveAvailableWalletMeta(phoenixGlobal: PhoenixGlobal, metadata: UserWalletMetadata)

expect suspend fun saveAvailableWalletMeta(
    phoenixGlobal: PhoenixGlobal,
    walletId: WalletId,
    name: String?,
    avatar: String,
    isHidden: Boolean
)

expect fun platformWriteSeed(
    log: Logger,
    phoenixGlobal: PhoenixGlobal,
    globalPrefs: GlobalPrefs,
    writingState: WritingSeedState,
    viewModelScope: CoroutineScope,
    mnemonics: List<String>,
    onWritingSeedError: (WritingSeedState.Error) -> Unit,
    onWritingSeedStateWriting: (WritingSeedState.Writing) -> Unit,
    isRestoringWallet: Boolean,
    isTorEnabled: Boolean,
    customElectrumServer: ElectrumConfig.Custom?,
    onSeedWritten: (WalletId) -> Unit
)


class NavigationViewModel(
    initialNavigationUIState: NavigationUIState,
    val phoenixGlobal: PhoenixGlobal,
    val nostrRepository: NostrRepository,
    val scope: CoroutineScope,
): ViewModel() {

    companion object {
        private const val TAG = "NavigationViewModel"

        fun factory(
            initialNavigationUIState: NavigationUIState,
            nostrRepository: NostrRepository,
            phoenixGlobal: PhoenixGlobal,
            scope: CoroutineScope,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NavigationViewModel(
                    phoenixGlobal = phoenixGlobal,
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

    val listWalletState = mutableStateOf<ListWalletState>(ListWalletState.Init)

    private val _availableWallets = MutableStateFlow<Map<WalletId, UserWallet>>(emptyMap())
    val availableWallets = _availableWallets.asStateFlow()

    private val _desiredWalletId = MutableStateFlow<WalletId?>(null)
    val desiredWalletId = _desiredWalletId.asStateFlow()
    val startWalletImmediately = MutableStateFlow(true)

    private val _activeWalletInUI = MutableStateFlow<ActiveWallet?>(null)
    val activeWalletInUI = _activeWalletInUI.asStateFlow()

    init {
        observeProfile()
    }

    private fun observeProfile() {
        logger.i("observeProfile")
        scope.launch(Dispatchers.IO) {
            delay(2_100) // Looking busy...

            logger.i("Navigation UI State is Landing")

            activeWalletInUI.collectLatest { activeWallet ->
                if (activeWallet == null) {
                    // TODO: No active wallet
                } else {
                    if (activeWallet.business != null) {
                        activeWallet.business.walletManager.keyManager.collectLatest { keyManager ->
                            keyManager?.nostrPublicKey()?.let { nostrPublicKey ->
                                val publicKey = nostrPublicKey.toHex()

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
                    } else {
                        // TODO: We have an error somewhere...
                    }
                }
            }


        }
    }


    fun setActiveWallet(walletId: WalletId, business: PhoenixBusiness) {
        val dataStoreManager = DataStoreManager(business)

        val userPrefs = dataStoreManager.loadUserPrefsForWallet(walletId = walletId)
        val internalPrefs = dataStoreManager.loadInternalPrefsForWallet( walletId = walletId)
        _activeWalletInUI.value = ActiveWallet(id = walletId, business = business, userPrefs = userPrefs, internalPrefs = internalPrefs)
        updateBusinessActiveInUI(walletId)
//        scheduleAutoLock()
    }

    fun listAvailableWallets(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, e ->
//            logger.error("error when initialising startup-view: ", e)
            listWalletState.value = ListWalletState.Error.Generic(e)
        }) {

            when (val result =
                loadAndDecryptSeed(phoenixGlobal)) {
                is DecryptSeedResult.Failure.SerializationError -> {
                    logger.error {"cannot deserialize seed file: "}
                    listWalletState.value = ListWalletState.Error.Serialization
                }
                is DecryptSeedResult.Failure.DecryptionError -> {
                    logger.e("cannot decrypt seed file: ", throwable = result.cause)
                    listWalletState.value = ListWalletState.Error.DecryptionError.GeneralException(result.cause)
                }
                is DecryptSeedResult.Failure.KeyStoreFailure -> {
                    logger.e("key store failure: ", throwable = result.cause)
                    listWalletState.value = ListWalletState.Error.DecryptionError.KeystoreFailure(result.cause)
                }
                is DecryptSeedResult.Failure.SeedFileUnreadable -> {
                    logger.e("aborting, unreadable seed file")
                    listWalletState.value = ListWalletState.Error.Generic(null)
                }
                is DecryptSeedResult.Failure.SeedInvalid -> {
                    logger.e("aborting, seed is invalid")
                    listWalletState.value = ListWalletState.Error.Generic(null)
                }

                is DecryptSeedResult.Failure.SeedFileNotFound -> {
                    listWalletState.value = ListWalletState.Success
                    _availableWallets.value = emptyMap()
                }

                is DecryptSeedResult.Success -> {

                    val metadataMap = getAvailableWalletsMeta(
                        phoenixGlobal
                    ).first()
                    result.userWalletsMap.forEach { (walletId, _) ->
                        if (metadataMap[walletId] == null) {
                            saveAvailableWalletMeta(
                                phoenixGlobal = phoenixGlobal,
                                walletId = walletId,
                                name = null,
                                avatar = WalletAvatars.list.random(),
                                isHidden = false
                            )
                        }
                    }
                    _availableWallets.value = result.userWalletsMap
                    listWalletState.value = ListWalletState.Success
                    viewModelScope.launch(Dispatchers.Main) {
                        onDone()
                    }
                }
            }
        }
    }

//    fun scheduleAutoLock() {
//        // TODO: Run this through an expect...
//        viewModelScope.launch {
//            autoLockHandler.removeCallbacksAndMessages(null)
//            val activeUserPrefs = activeWalletInUI.first()?.userPrefs ?: return@launch
//
//            val biometricLockEnabled = activeUserPrefs.getLockBiometricsEnabled.first()
//            val customPinLockEnabled = activeUserPrefs.getLockPinEnabled.first()
//            val autoLockDelay = activeUserPrefs.getAutoLockDelay.first()
//
//            if ((biometricLockEnabled || customPinLockEnabled) && autoLockDelay != Duration.INFINITE) {
//                autoLockHandler.postDelayed(autoLockRunnable, autoLockDelay.inWholeMilliseconds)
//            }
//        }
//    }

    /** Clears the active wallet and signals the startup screen to load the given [walletId]. */
    fun switchToWallet(walletId: WalletId) {
        _desiredWalletId.value = walletId
        _activeWalletInUI.value = null
    }

    /** Clears the active wallet. It does not affect [desiredWalletId]. The UI may still auto-open a specific wallet, if [desiredWalletId] is not null. */
    fun clearActiveWallet() {
        _activeWalletInUI.value = null
    }

    /** Resets the active wallet and [desiredWalletId]. The UI will redirect to the startup screen with the wallets selector prompt. */
    fun resetToSelector() {
        _desiredWalletId.value = null
        _activeWalletInUI.value = null
        startWalletImmediately.value = false
    }

    override fun onCleared() {
        super.onCleared()
        logger.i("AppViewModel cleared")
    }

    fun getPhoenixGlobalPrefs(): GlobalPrefs {
        return getGlobalPrefs(
            phoenixGlobal
        )
    }

    // wallet initialisation options -- to be saved to user prefs once the wallet has been created and we know its walletId
    var isTorEnabled = mutableStateOf(false)
    var customElectrumServer = mutableStateOf<ElectrumConfig.Custom?>(null)

    /** Monitors the writing of a seed on disk ; used by the restore view and the create view thru [writeSeed]. */
    var writingState by mutableStateOf<WritingSeedState>(
        WritingSeedState.Init
    )
        private set


    /**
     * Attempts to write a seed on disk and updates the view model state. If a seed already
     * exists on disk, this method will put the [writingState] in error.
     */
    fun writeSeed(
        mnemonics: List<String>,
        isRestoringWallet: Boolean,
        onSeedWritten: (WalletId) -> Unit
    ) {
        platformWriteSeed(
            log = logger,
            phoenixGlobal = phoenixGlobal,
            globalPrefs = getPhoenixGlobalPrefs(),
            writingState = writingState,
            viewModelScope = viewModelScope,
            mnemonics = mnemonics,
            onWritingSeedError = { writingSeedStateError ->
                writingState = writingSeedStateError
            },
            onWritingSeedStateWriting = { writing ->
                writingState = writing
            },
            isRestoringWallet = isRestoringWallet,
            isTorEnabled = isTorEnabled.value,
            customElectrumServer = customElectrumServer.value,
            onSeedWritten = { walletId ->
                onSeedWritten(walletId)
            }
        )
    }

}