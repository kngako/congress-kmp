package press.mantra.compose.ui.view.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.phoenix.data.ActiveWallet
import fr.acinq.phoenix.managers.nostrPrivateKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import press.mantra.compose.database.model.types.ChatRoomType
import press.mantra.compose.managers.ChillDkgRitualManager
import press.mantra.compose.managers.MarmotGroupCreation
import press.mantra.compose.managers.MarmotGroupName
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.ui.composable.navigation.routes.ChatRoomMessagingRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.view.state.SelectSubgroupAdminsUIState

/**
 * Who runs a subgroup, what it is called, and on what quorum.
 *
 * All three are settled here and nowhere else. The name and the admin set are
 * carried into the birth certificate, so they are what the parent's admins will
 * be asked to approve; the threshold cannot move afterwards at all, because
 * ChillDKG hashes it and the host keys into the session identity, so `t` is fixed
 * the moment the proposal goes out and a group that disagrees about it gets no
 * key rather than a weak one.
 *
 * It is also the one value picked *for* other people, and consent survives that:
 * `t` rides on the proposal, every invitee's `acceptProposal` re-checks it against
 * `quorumRange` and drops a proposal outside it, and the host-key approval gate is
 * where each of them agrees to the `t`-of-`n` they can now see.
 *
 * Confirming opens the ceremony -- a NIP-17 room over the picked admins, and a
 * ritual proposal in it -- and hands over to `DkgRitualViewModel`, which drives
 * the remaining three steps. See `docs/subgroups.md`.
 */
class SelectSubgroupAdminsViewModel(
    val activeUserPublicKey: HexKey,
    val parentChatRoomId: String,
    initialUIState: SelectSubgroupAdminsUIState,
    val activeWalletStateFlow: StateFlow<ActiveWallet?>,
    val chatRepository: ChatRepository,
    val dkgRepository: DkgRepository,
): ViewModel() {

    var uiState: SelectSubgroupAdminsUIState by mutableStateOf(initialUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    /** Members ticked, in the order they were ticked. The coordinator is never here. */
    val selectedPublicKeys = mutableStateListOf<HexKey>()

    /**
     * What the coordinator typed, bare.
     *
     * The `#` is drawn beside the field rather than pushed into this state:
     * normalising on every keystroke would move the caret out from under somebody
     * halfway through a word. It is added by `MarmotGroupName.of` where the
     * subgroup's name is actually used -- in the certificate the parent signs and
     * on the room itself -- which is idempotent, so the two agree.
     */
    val name: MutableState<String> = mutableStateOf("")

    val threshold: MutableState<Int> = mutableStateOf(ChatRoomType.MINIMUM_QUORUM)

    fun initiate() {
        viewModelScope.launch(Dispatchers.IO) {
            val parentRoom = chatRepository.getChatRoomByIdentifier(parentChatRoomId)
            if (parentRoom == null) {
                uiState = SelectSubgroupAdminsUIState.Error("Couldn't load this group")
                return@launch
            }

            // Everybody but this device, since the coordinator is a participant by
            // construction and is not theirs to tick or untick.
            val candidates = parentRoom.localParticipants
                .distinctBy { it.participant.participantPublicKey }
                .filterNot { it.participant.participantPublicKey == activeUserPublicKey }

            uiState = SelectSubgroupAdminsUIState.Loaded(
                parentRoom = parentRoom,
                candidates = candidates,
            )

            resolveKeyPackages(candidates.map { it.participant.participantPublicKey })
        }
    }

    /**
     * Asks every candidate's key package up front.
     *
     * The same move `SelectChatRoomMembersViewModel` makes for group creation, and
     * here it is doing more than saving a round trip. A key package is
     * one-time-use -- `NostrDao` marks the bundle consumed as the device processes
     * its own Welcome -- so every group a member joins burns one, and a member who
     * has not published since has none left. `MarmotGroupCreation` would refuse to
     * create the room for that, correctly, but only after a ChillDKG, a parent
     * quorum and a child quorum had all completed, each needing every selected
     * admin present. Finding out here costs nothing and finding out there costs
     * three ceremonies.
     */
    private suspend fun resolveKeyPackages(publicKeys: List<HexKey>) {
        val resolved = MarmotGroupCreation.keyPackagesFor(chatRepository, publicKeys)
        val missing = resolved.filter { it.second == null }.map { it.first }.toSet()

        val loaded = uiState as? SelectSubgroupAdminsUIState.Loaded ?: return
        uiState = loaded.copy(withoutKeyPackage = missing, isResolvingKeyPackages = false)

        // A member who cannot be welcomed cannot be an admin of a room they are not
        // in, so unpick them rather than leaving a selection that confirm will
        // refuse.
        selectedPublicKeys.removeAll(missing)
    }

    fun toggleMember(publicKey: HexKey) {
        if (isActionPending.value) return

        val loaded = uiState as? SelectSubgroupAdminsUIState.Loaded ?: return
        if (publicKey in loaded.withoutKeyPackage) return

        if (!selectedPublicKeys.remove(publicKey)) {
            selectedPublicKeys.add(publicKey)
        }

        // The range moves with the admin count, so a threshold picked at one size
        // has to be pulled back into the range at another.
        threshold.value = threshold.value.coerceIn(quorumRange())
    }

    fun isSelected(publicKey: HexKey): Boolean = selectedPublicKeys.contains(publicKey)

    fun setName(value: String) {
        if (isActionPending.value) return

        name.value = value
    }

    /** The subgroup's admins: everyone ticked, plus the coordinator. */
    fun adminCount(): Int = selectedPublicKeys.size + 1

    fun quorumRange(): IntRange =
        ChatRoomType.quorumRange(maxOf(adminCount(), ChatRoomType.MINIMUM_QUORUM))

    fun setThreshold(value: Int) {
        if (isActionPending.value) return

        threshold.value = value.coerceIn(quorumRange())
    }

    /**
     * Why this cannot be confirmed yet, or null when it can.
     *
     * Every message names what to do rather than what is wrong, because each of
     * these is something the user can fix from where they are standing.
     */
    suspend fun refusal(): String? {
        val loaded = uiState as? SelectSubgroupAdminsUIState.Loaded ?: return "Still loading"

        if (name.value.isBlank()) return "Give the subgroup a name."

        if (loaded.isResolvingKeyPackages) return "Checking who can be added…"

        val unreachable = selectedPublicKeys.filter { it in loaded.withoutKeyPackage }
        if (unreachable.isNotEmpty()) {
            return "Some of the people picked have no key package. Ask them to " +
                "publish one, then try again."
        }

        return chatRepository.refuseSubgroup(
            parentChatRoomId = parentChatRoomId,
            adminPublicKeys = selectedPublicKeys.toSet(),
            coordinatorPublicKey = activeUserPublicKey
        )
    }

    /**
     * Opens the ceremony and hands over to the ritual screen.
     *
     * The room is stood up first and the proposal is the first thing out of it,
     * exactly as a robust group is created: standing up a NIP-17 room sends
     * nothing to anybody, so the proposal is how the other admins hear of it at
     * all -- which is why it carries the name and the parent.
     */
    fun confirm(onNavigateToRoute: (Route) -> Unit) {
        if (isActionPending.value) return

        val loaded = uiState as? SelectSubgroupAdminsUIState.Loaded ?: return

        val nostrPrivateKey = activeWalletStateFlow.value
            ?.business?.walletManager?.keyManager?.value?.nostrPrivateKey()
        if (nostrPrivateKey == null) {
            uiState = SelectSubgroupAdminsUIState.Error("Couldn't read your keys. Please try again.")
            return
        }

        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            // Checked again here rather than trusted from the button: a screen not
            // drawing something is not a guard, and the key-package answer can go
            // stale while the screen is open.
            val refusal = refusal()
            if (refusal != null) {
                isActionPending.value = false
                uiState = SelectSubgroupAdminsUIState.Error(refusal)
                return@launch
            }

            // The ceremony room keeps the bare name. It is a NIP-17 room -- where
            // the subgroup is made, not the subgroup -- and the `#` is what tells
            // those two rows apart afterwards, so putting it on both would spend
            // the mark to say nothing.
            //
            // The subgroup gets it twice over, and never from here:
            // `proposeBirthCertificate` normalises the name the parent signs and
            // `MarmotGroupCreation` normalises the name the room carries, both
            // reading this subject. `MarmotGroupName.of` is idempotent, so those
            // are the same string -- which they have to be, or the room is not
            // called what its parent certified.
            val ceremonyRoom = chatRepository.createNip17ChatRoom(
                userPublicKey = activeUserPublicKey,
                participantPublicKeys = selectedPublicKeys.toList(),
                subject = name.value.trim(),
                description = "Making ${MarmotGroupName.of(name.value)} a subgroup of " +
                    (loaded.parentRoom.chatRoom.subject ?: "this group") + "."
            )

            if (ceremonyRoom == null) {
                isActionPending.value = false
                uiState = SelectSubgroupAdminsUIState.Error(
                    "Couldn't start the subgroup's key ceremony. Please try again."
                )
                return@launch
            }

            val session = dkgRepository.proposeRitual(
                localChatRoom = ceremonyRoom,
                userPublicKey = activeUserPublicKey,
                nostrPrivateKey = nostrPrivateKey.value.toByteArray(),
                threshold = threshold.value,
                parentChatRoomId = parentChatRoomId
            )

            isActionPending.value = false

            if (session == null) {
                logger.e("Created ${ceremonyRoom.chatRoom.id} without a key ceremony")
                uiState = SelectSubgroupAdminsUIState.Error(
                    "Couldn't start the subgroup's key ceremony. Please try again."
                )
                return@launch
            }

            withContext(Dispatchers.Main) {
                // The transcript, not the shared-key screen. The ceremony is
                // already open and its first line is already in this room, so what
                // the coordinator needs is to watch it and answer the requests as
                // they arrive -- which is the same path every other member takes,
                // through the ritual notices that name their own ceremony.
                //
                // Sending them to the shared-key screen instead was actively
                // harmful: that screen's job is to *offer* a ceremony to a room
                // that has none, so the coordinator arrived at a "Start key
                // ceremony" button seconds after starting one, and pressing it
                // opened a second -- for no subgroup, so `proposeRitual`'s guard
                // did not recognise it as a duplicate -- which then buried the
                // subgroup's under the room's newest.
                onNavigateToRoute(
                    ChatRoomMessagingRoute(
                        activeUserPublicKey = activeUserPublicKey,
                        chatRoomId = ceremonyRoom.chatRoom.id,
                        relayHint = null
                    )
                )
            }
        }
    }

    companion object {
        private const val TAG = "SelectSubgroupAdminsViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            parentChatRoomId: String,
            initialUIState: SelectSubgroupAdminsUIState = SelectSubgroupAdminsUIState.Loading,
            activeWalletStateFlow: StateFlow<ActiveWallet?>,
            chatRepository: ChatRepository,
            dkgRepository: DkgRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SelectSubgroupAdminsViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    parentChatRoomId = parentChatRoomId,
                    initialUIState = initialUIState,
                    activeWalletStateFlow = activeWalletStateFlow,
                    chatRepository = chatRepository,
                    dkgRepository = dkgRepository
                )
            }
        }
    }
}
