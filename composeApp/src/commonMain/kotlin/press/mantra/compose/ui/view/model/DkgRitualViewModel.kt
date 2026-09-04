package press.mantra.compose.ui.view.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.database.model.types.ChatRoomType
import press.mantra.compose.managers.ChillDkgRitualManager
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.ui.view.state.DkgRitualUIState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import fr.acinq.phoenix.data.ActiveWallet
import fr.acinq.phoenix.managers.nostrPrivateKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Watches a group's ChillDKG ritual, and opens one on request.
 *
 * Nothing here drives the protocol — the ritual advances in
 * [ChillDkgRitualManager] as messages arrive on the inbound path, so this only
 * has to read the session and show it moving.
 */
class DkgRitualViewModel(
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    initialDkgRitualUIState: DkgRitualUIState,
    val activeWalletStateFlow: StateFlow<ActiveWallet?>,
    val chatRepository: ChatRepository,
    val dkgRepository: DkgRepository,
): ViewModel() {

    var dkgRitualUIState: DkgRitualUIState by mutableStateOf(initialDkgRitualUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    /** Watches the current session's messages; replaced whenever the session changes. */
    private var messageObserver: Job? = null

    /**
     * The quorum the ritual will generate a key for. Pre-filled with the same
     * majority default the group-creation screen offers.
     *
     * TODO: the quorum chosen at creation time is not persisted on ChatRoom, so
     *  it cannot be read back here. Storing it there would let this default to
     *  what the group was actually promised rather than re-deriving it.
     */
    val threshold: MutableState<Int> = mutableStateOf(ChatRoomType.MINIMUM_QUORUM)

    /**
     * Whether this device can open a ceremony right now.
     *
     * Any member may: ChillDKG gives the coordinator no say in the outcome, and a
     * NIP-17 room records no creator to reserve the job for. What rules it out is
     * a ceremony already running, or a group too small to split a key across.
     */
    fun canStartRitual(): Boolean {
        val loaded = dkgRitualUIState as? DkgRitualUIState.Loaded ?: return false

        if (loaded.session != null && loaded.session.stage != DkgRitualStage.FAILED) return false

        return ChillDkgRitualManager.canRunRitual(loaded.localChatRoom)
    }

    fun quorumRange(): IntRange {
        val loaded = dkgRitualUIState as? DkgRitualUIState.Loaded
            ?: return ChatRoomType.quorumRange(ChatRoomType.MINIMUM_QUORUM)

        // Below the minimum the range runs backwards and is empty, which `coerceIn`
        // rejects outright. There is no ritual to run at that size either.
        return ChatRoomType.quorumRange(
            maxOf(loaded.participantCount, ChatRoomType.MINIMUM_QUORUM)
        )
    }

    fun setThreshold(value: Int) {
        if (isActionPending.value) return

        threshold.value = value.coerceIn(quorumRange())
    }

    fun initiate() {
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)
            if (localChatRoom == null) {
                dkgRitualUIState = DkgRitualUIState.Error("Couldn't load this chat")
                return@launch
            }

            dkgRitualUIState = DkgRitualUIState.Loaded(localChatRoom = localChatRoom)
            threshold.value = ChatRoomType
                .defaultQuorum(ChillDkgRitualManager.memberPublicKeys(localChatRoom).size)
                .coerceIn(quorumRange())

            dkgRepository.observeLatestSessionForChatRoom(chatRoomId).collect { session ->
                val loaded = (dkgRitualUIState as? DkgRitualUIState.Loaded)
                    ?: DkgRitualUIState.Loaded(localChatRoom = localChatRoom)

                dkgRitualUIState = loaded.copy(session = session)

                // Re-point the message watcher at whatever session is current. An
                // abandoned ritual's counts must not keep ticking over the new one.
                messageObserver?.cancel()
                if (session != null) {
                    messageObserver = viewModelScope.launch(Dispatchers.IO) {
                        dkgRepository.observeMessages(session.id).collect { messages ->
                            val current = dkgRitualUIState as? DkgRitualUIState.Loaded ?: return@collect

                            fun senders(kind: Kind) = messages
                                .filter { it.kind == kind }
                                .map { it.participantPublicKey }
                                .toSet()

                            dkgRitualUIState = current.copy(
                                hostKeyParticipants = senders(DkgRitualEvents.HOST_KEY),
                                round1Participants = senders(DkgRitualEvents.ROUND_1),
                                round2Participants = senders(DkgRitualEvents.ROUND_2)
                            )
                        }
                    }
                }
            }
        }
    }

    /** Opens a ritual, making this device its coordinator, and publishes the proposal. */
    fun startRitual() {
        if (isActionPending.value) return

        val loaded = dkgRitualUIState as? DkgRitualUIState.Loaded ?: return

        val nostrPrivateKey = activeWalletStateFlow.value?.business?.walletManager?.keyManager?.value?.nostrPrivateKey()
        if (nostrPrivateKey == null) {
            dkgRitualUIState = DkgRitualUIState.Error("Couldn't read your keys. Please try again.")
            return
        }

        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val session = dkgRepository.proposeRitual(
                localChatRoom = loaded.localChatRoom,
                userPublicKey = activeUserPublicKey,
                nostrPrivateKey = nostrPrivateKey.value.toByteArray(),
                threshold = threshold.value
            )

            isActionPending.value = false

            if (session == null) {
                logger.e("Failed to start DKG ritual for $chatRoomId")
                dkgRitualUIState = DkgRitualUIState.Error("Couldn't start the key ceremony. Please try again.")
            }
            // On success the session flow above delivers the new row; no need to
            // set it here and risk racing the observer.
        }
    }

    override fun onCleared() {
        messageObserver?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "DkgRitualViewModel"

        fun factory(
            chatRoomId: String,
            activeUserPublicKey: HexKey,
            initialDkgRitualUIState: DkgRitualUIState = DkgRitualUIState.Loading,
            activeWalletStateFlow: StateFlow<ActiveWallet?>,
            chatRepository: ChatRepository,
            dkgRepository: DkgRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DkgRitualViewModel(
                    chatRoomId = chatRoomId,
                    activeUserPublicKey = activeUserPublicKey,
                    initialDkgRitualUIState = initialDkgRitualUIState,
                    activeWalletStateFlow = activeWalletStateFlow,
                    chatRepository = chatRepository,
                    dkgRepository = dkgRepository
                )
            }
        }
    }
}
