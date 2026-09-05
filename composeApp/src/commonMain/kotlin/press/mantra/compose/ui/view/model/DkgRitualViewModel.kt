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
import press.mantra.compose.database.model.types.ChatRoomType
import press.mantra.compose.managers.ChillDkgRitualManager
import press.mantra.compose.extensions.toHex
import press.mantra.compose.managers.SharedKeyDerivation
import press.mantra.compose.nostr.Relays
import press.mantra.compose.ui.composable.navigation.routes.ChatRoomMessagingRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers.Main
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
     */
    fun createAdminGroup(onNavigateToRoute: (Route) -> Unit) {
        if (isActionPending.value) return

        val loaded = dkgRitualUIState as? DkgRitualUIState.Loaded ?: return
        val thresholdPublicKey = loaded.session?.thresholdPublicKey ?: return

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

        viewModelScope.launch(Dispatchers.IO) {
            // Derived ids make this reachable twice -- a second tap, or another
            // member having got there first. Joining what exists beats minting a
            // rival group on the same id.
            val existing = chatRepository.getChatRoomByIdentifier(groupId)
            if (existing != null) {
                isActionPending.value = false
                withContext(Main) {
                    onNavigateToRoute(
                        ChatRoomMessagingRoute(
                            activeUserPublicKey = activeUserPublicKey,
                            chatRoomId = existing.chatRoom.id,
                            relayHint = null
                        )
                    )
                }
                return@launch
            }

            val relays = Relays.DefaultDMRelayList.map { it.url }

            // Built directly rather than through MarmotGroupData.bootstrap, which
            // hardcodes a single admin. Baked into the epoch-0 GroupContext so later
            // invitees get a populated group from their welcome instead of chasing a
            // bootstrap commit that predates their membership.
            val metadata = MarmotGroupData(
                nostrGroupId = groupId,
                name = name,
                // Carries the derivation path. MIP-01 has no field for it, and the
                // path is what rebuilds the TweakCache a signing session needs --
                // recomputable from the constant only for as long as the constant
                // never changes.
                description = SharedKeyDerivation.describe(
                    purpose = "Admins of ${loaded.localChatRoom.chatRoom.subject ?: "the group"}."
                ),
                adminPubkeys = members.toList(),
                relays = relays
            )

            val signingKeyPair = Ed25519.generateKeyPair()
            val group = MlsGroup.create(keyPair.pubKey, signingKeyPair.privateKey, listOf(metadata.toExtension()))

            // Keyed on the Marmot nostrGroupId, not MlsGroup's own groupId: they are
            // unrelated 32-byte values, and every inbound path resolves rooms by the
            // former.
            val localChatRoom = chatRepository.getOrCreateChatRoom(
                chatRoomId = groupId,
                activeUserPublicKey = activeUserPublicKey,
                relayHint = null,
                defaultSubject = name,
                description = metadata.description,
                mlsGroupState = group.saveState().encodeTls().toHex()
            )

            if (localChatRoom == null) {
                isActionPending.value = false
                dkgRitualUIState = DkgRitualUIState.Error("Couldn't create the admin group. Please try again.")
                return@launch
            }

            val notAdded = inviteAdmins(groupId, members.filterNot { it == activeUserPublicKey })

            isActionPending.value = false

            if (notAdded.isNotEmpty()) {
                logger.w("Admin group $groupId created without ${notAdded.size} member(s): $notAdded")
            }

            withContext(Main) {
                onNavigateToRoute(
                    ChatRoomMessagingRoute(
                        activeUserPublicKey = activeUserPublicKey,
                        chatRoomId = localChatRoom.chatRoom.id,
                        relayHint = null
                    )
                )
            }
        }
    }

    /**
     * Adds each member to the freshly created admin room, returning those who could
     * not be added.
     *
     * A Marmot invite needs the invitee's published key package, so a member who has
     * never published one cannot be added here and has to be invited later. That is
     * reported rather than treated as failure: a room with most of the group in it
     * is more useful than no room.
     */
    /**
     * Adds every admin to the freshly created room in one commit, returning those
     * that could not be added.
     *
     * Batched rather than invited one at a time: the whole membership is known here,
     * and inviting sequentially creates an epoch per member, each of whose commits
     * races the previous member's Welcome. A member who loses that race is silently
     * stuck an epoch behind. See docs/marmot-membership.md.
     *
     * A Marmot invite still needs the invitee's published key package, so a member
     * who has never published one cannot be added and has to be invited later. That
     * is reported rather than treated as failure: a room with most of the group in
     * it is more useful than no room.
     */
    private suspend fun inviteAdmins(groupId: String, peers: List<HexKey>): List<HexKey> {
        val keyPackages = coroutineScope {
            peers.map { publicKey ->
                async {
                    publicKey to withTimeoutOrNull(KEY_PACKAGE_LOOKUP_TIMEOUT) {
                        chatRepository.observeMarmotKeyPackageForPublicKey(publicKey)
                            .filterNotNull()
                            .first()
                    }
                }
            }.awaitAll()
        }

        val withoutKeyPackage = keyPackages.filter { it.second == null }.map { it.first }
        withoutKeyPackage.forEach { logger.w("No key package for $it; leaving them out of $groupId") }

        val addable = keyPackages.mapNotNull { (publicKey, keyPackage) ->
            keyPackage?.let { publicKey to it }
        }
        if (addable.isEmpty()) return withoutKeyPackage

        val localChatRoom = chatRepository.getChatRoomByIdentifier(groupId)
        if (localChatRoom == null) {
            logger.e("Admin group $groupId disappeared before its members were added")
            return peers
        }

        val notAdded = runCatching {
            chatRepository.addMembers(localChatRoom = localChatRoom, peers = addable)
        }.onFailure {
            logger.e("Failed to add members to admin group $groupId", it)
        }.getOrElse { addable.map { (publicKey, _) -> publicKey } }

        return withoutKeyPackage + notAdded
    }

    override fun onCleared() {
        messageObserver?.cancel()
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
