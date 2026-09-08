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
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.database.model.types.FrostSigningStage
import press.mantra.compose.database.model.types.ChatRoomType
import press.mantra.compose.managers.ChillDkgRitualManager
import press.mantra.compose.managers.MarmotGroupCreation
import press.mantra.compose.managers.SharedKeyDerivation
import press.mantra.compose.ui.composable.navigation.routes.ChatRoomMessagingRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers.Main
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.ui.view.state.DkgRitualUIState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
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

    /** Watches the room's signing sessions, which is where the key state is agreed. */
    private var keyStateObserver: Job? = null

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

            observeKeyState()

            dkgRepository.observeLatestSessionForChatRoom(chatRoomId).collect { session ->
                val loaded = (dkgRitualUIState as? DkgRitualUIState.Loaded)
                    ?: DkgRitualUIState.Loaded(localChatRoom = localChatRoom)

                dkgRitualUIState = loaded.copy(
                    session = session,
                    pendingApproval = session?.let { dkgRepository.pendingApproval(it) }
                )

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
                                round2Participants = senders(DkgRitualEvents.ROUND_2),
                                // Recomputed here as well as on the session, because
                                // whether round 1 can be asked for depends on how many
                                // host keys have arrived, which only this flow sees.
                                pendingApproval = current.session
                                    ?.let { dkgRepository.pendingApproval(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Follows the group agreeing what its #admins room will sign with.
     *
     * Nothing here watches a [GroupKeyState] row, because there is no room yet
     * for one to belong to -- being able to follow this before the room exists
     * is the point. What is watched instead is the session, for how the signing
     * is going, and the signed event, for whether it finished.
     *
     * The room a ceremony runs in signs nothing else, so the newest session in
     * it is the key state's -- but it is matched on the kind it carries anyway,
     * so a room that one day signs something else does not confuse the two.
     */
    private fun observeKeyState() {
        keyStateObserver?.cancel()
        keyStateObserver = viewModelScope.launch(Dispatchers.IO) {
            // Two flows because they answer two questions off two tables. How
            // the signing is going is the session row; whether it finished is
            // the signed event, and only that second one can be asked at all
            // before there is a room -- which is the whole point of asking it.
            launch {
                dkgRepository.observeSigningSessions(chatRoomId).collect { sessions ->
                    val loaded = dkgRitualUIState as? DkgRitualUIState.Loaded ?: return@collect

                    dkgRitualUIState = loaded.copy(
                        keyStateSession = sessions.firstOrNull { local ->
                            local.items.any { item ->
                                runCatching {
                                    Event.fromJson(item.unsignedEventJson).kind
                                }.getOrNull() == GroupKeyStateEvent.KIND
                            }
                        }?.session
                    )
                }
            }

            launch {
                dkgRepository.observeSignedGroupKeyState(chatRoomId).collect { state ->
                    val loaded = dkgRitualUIState as? DkgRitualUIState.Loaded ?: return@collect

                    dkgRitualUIState = loaded.copy(isKeyStateSigned = state != null)
                }
            }
        }
    }

    /**
     * Asks the group to sign what its #admins room will sign with.
     *
     * The step before the room exists, and the reason it can be created knowing
     * its own key state rather than being told afterwards. Offered to the
     * ceremony's coordinator, who is also the member who will create the room.
     *
     * Nothing here waits for the answer: the signing runs on arriving messages
     * like everything else, and the screen follows it through [observeKeyState].
     */
    fun proposeKeyState() {
        if (isActionPending.value) return

        val loaded = dkgRitualUIState as? DkgRitualUIState.Loaded ?: return
        val session = loaded.session ?: return
        if (session.thresholdPublicKey == null) return

        // Nothing to ask for, and nobody to ask twice. A failed session is the
        // one case a second proposal is right, and it has to be a new session
        // rather than that one resumed -- see `FrostSigningManager`, which is
        // why `propose` mints a fresh id every time.
        if (loaded.isKeyStateSigned) return
        loaded.keyStateSession?.let { if (it.stage != FrostSigningStage.FAILED) return }

        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val signing = dkgRepository.proposeGroupKeyState(
                localChatRoom = loaded.localChatRoom,
                userPublicKey = activeUserPublicKey,
                session = session
            )

            isActionPending.value = false

            if (signing == null) {
                logger.e("Failed to propose the key state for $chatRoomId")
                dkgRitualUIState = DkgRitualUIState.Error(
                    "Couldn't ask the group to agree its signing key. Please try again."
                )
            }
            // On success the session flow above delivers the new row; no need to
            // set it here and risk racing the observer.
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

    /**
     * Agrees to whatever the ritual is currently waiting on from this device.
     *
     * Takes the step rather than reading it back from the state so a screen opened
     * for one step cannot approve a different one it has since moved on to — the
     * manager checks it against the ritual again and ignores a mismatch.
     */
    fun approve(step: DkgApprovalStep) {
        if (isActionPending.value) return

        val loaded = dkgRitualUIState as? DkgRitualUIState.Loaded ?: return
        val session = loaded.session ?: return

        val nostrPrivateKey = activeWalletStateFlow.value?.business?.walletManager?.keyManager?.value?.nostrPrivateKey()
        if (nostrPrivateKey == null) {
            dkgRitualUIState = DkgRitualUIState.Error("Couldn't read your keys. Please try again.")
            return
        }

        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            dkgRepository.approve(
                localChatRoom = loaded.localChatRoom,
                sessionId = session.id,
                step = step,
                nostrPrivateKey = nostrPrivateKey.value.toByteArray()
            )

            isActionPending.value = false
            // The session flow delivers the updated row and with it the next pending
            // step, so nothing is set here.
        }
    }

    /**
     * Opens the group's admin room: a Marmot/MLS chat whose every member is an
     * admin, keyed on an id derived from the shared key.
     *
     * The room the ceremony ran in is NIP-17, where nobody administers anything.
     * This gives the same people a room where every one of them can act, which is
     * the shape a group that has just made a t-of-n key is asking for.
     *
     * Derived rather than random, unlike every other Marmot room. Every member's
     * device can compute the id from the ceremony they all took part in, so the
     * room is addressable without being announced, and two members racing to create
     * it produce the same id instead of two rival rooms -- which is why this returns
     * early to the existing room rather than minting a second one.
     *
     * Only once the group has signed what the room signs with. That statement is
     * agreed in the NIP-17 room the ceremony ran in, before there is a Marmot
     * room to hold it -- see `GroupKeyStateManager.propose` -- so by here it is
     * a fact the room is created *knowing*, rather than the first thing it has
     * to go and ask about.
     */
    fun createAdminGroup(onNavigateToRoute: (Route) -> Unit) {
        if (isActionPending.value) return

        val loaded = dkgRitualUIState as? DkgRitualUIState.Loaded ?: return
        val session = loaded.session ?: return
        val thresholdPublicKey = session.thresholdPublicKey ?: return

        // The screen offers nothing until the group has signed, so this is a
        // guard rather than a path anybody takes.
        if (!loaded.isKeyStateSigned) return

        val nostrPrivateKey = activeWalletStateFlow.value?.business?.walletManager?.keyManager?.value?.nostrPrivateKey()
        if (nostrPrivateKey == null) {
            dkgRitualUIState = DkgRitualUIState.Error("Couldn't read your keys. Please try again.")
            return
        }

        val keyPair = KeyPair(privKey = nostrPrivateKey.value.toByteArray())
        val groupId = SharedKeyDerivation.marmotGroupId(thresholdPublicKey)

        // Everyone the ceremony was run with, this device included. These are the
        // admins: the whole point of the room is that the members who hold shares of
        // the key can all act in it.
        val members = ChillDkgRitualManager.memberPublicKeys(loaded.localChatRoom)
        val name = "${loaded.localChatRoom.chatRoom.subject ?: "Group"} (#admins)"

        isActionPending.value = true
        dkgRitualUIState = loaded.copy(adminGroupBlockedOn = emptyList())

        viewModelScope.launch(Dispatchers.IO) {
            val outcome = chatRepository.createMarmotGroup(
                groupId = groupId,
                name = name,
                purpose = "Admins of ${loaded.localChatRoom.chatRoom.subject ?: "the group"}.",
                adminPublicKeys = members,
                userPublicKey = activeUserPublicKey,
                keyPair = keyPair
            )

            isActionPending.value = false

            when (outcome) {
                is MarmotGroupCreation.Outcome.BlockedOn -> {
                    // Named rather than counted. A member cannot publish a key
                    // package on somebody else's behalf, so the only useful thing
                    // to say is whose door to knock on.
                    dkgRitualUIState = loaded.copy(
                        adminGroupBlockedOn = outcome.missing.map { publicKey ->
                            loaded.ritualMembers
                                .firstOrNull { it.participant.participantPublicKey == publicKey }
                                ?.profile
                                ?.humanReadableNameOrPubkey()
                                ?: publicKey.take(12)
                        }
                    )
                }

                is MarmotGroupCreation.Outcome.Failed -> {
                    dkgRitualUIState = DkgRitualUIState.Error(outcome.reason)
                }

                is MarmotGroupCreation.Outcome.Created -> withContext(Main) {
                    onNavigateToRoute(
                        ChatRoomMessagingRoute(
                            activeUserPublicKey = activeUserPublicKey,
                            chatRoomId = outcome.room.chatRoom.id,
                            relayHint = null
                        )
                    )
                }

                is MarmotGroupCreation.Outcome.Existing -> withContext(Main) {
                    onNavigateToRoute(
                        ChatRoomMessagingRoute(
                            activeUserPublicKey = activeUserPublicKey,
                            chatRoomId = outcome.room.chatRoom.id,
                            relayHint = null
                        )
                    )
                }
            }
        }
    }

    override fun onCleared() {
        messageObserver?.cancel()
        keyStateObserver?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "DkgRitualViewModel"

        /** Matches SelectChatRoomTypeViewModel: relays are not always prompt. */
        private const val KEY_PACKAGE_LOOKUP_TIMEOUT = 10_000L

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
