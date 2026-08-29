package press.mantra.compose.ui.view.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.types.ChatRoomType
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.Relays
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.navigation.routes.ChatRoomMessagingRoute
import press.mantra.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.view.state.SelectChatRoomTypeUIState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.utils.RandomInstance
import fr.acinq.phoenix.data.ActiveWallet
import fr.acinq.phoenix.managers.nostrPrivateKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Last step of group creation: how the group should be run, and then actually
 * building it.
 *
 * The choice is not cosmetic — it decides who lands in the epoch-0
 * `admin_pubkeys` list, so the group can only be minted once it has been made.
 */
class SelectChatRoomTypeViewModel(
    val activeUserPublicKey: HexKey,
    val name: String,
    val description: String?,
    val memberPublicKeys: List<HexKey>,
    initialSelectChatRoomTypeUIState: SelectChatRoomTypeUIState,
    val activeWalletStateFlow: StateFlow<ActiveWallet?>,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository,
): ViewModel() {

    var selectChatRoomTypeUIState: SelectChatRoomTypeUIState by mutableStateOf(initialSelectChatRoomTypeUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    /** Convenient until the user says otherwise: it is the choice that always works. */
    val selectedChatRoomType: MutableState<ChatRoomType> = mutableStateOf(ChatRoomType.CONVENIENT)

    /**
     * Set as soon as the room exists. Creating a group is not idempotent, so a second
     * tap after a partially failed invite round has to reuse this room rather than
     * mint another one.
     */
    val createdChatRoomId: MutableState<String?> = mutableStateOf(null)

    /** Members the group was created without, because no key package ever showed up. */
    val membersNotAdded = mutableStateListOf<HexKey>()

    /**
     * Everyone who administers the group under [ChatRoomType.ROBUST]: the picked
     * members plus the creator.
     */
    val adminCount: Int = (memberPublicKeys + activeUserPublicKey).distinct().size

    /** How many of [adminCount] admins a change needs, under [ChatRoomType.ROBUST]. */
    val approvalThreshold: Int = ChatRoomType.approvalThreshold(adminCount)

    /** Whether this group is big enough to be run as [ChatRoomType.ROBUST]. */
    val isRobustAvailable: Boolean = ChatRoomType.isRobustAvailable(adminCount)

    /** Why robust is off the table, or null when it is available. */
    val robustUnavailableReason: String? = if (isRobustAvailable) {
        null
    } else {
        val shortfall = ChatRoomType.MINIMUM_ROBUST_GROUP_SIZE - adminCount
        val missing = if (shortfall == 1) "1 more person" else "$shortfall more people"

        "Needs at least ${ChatRoomType.MINIMUM_ROBUST_GROUP_SIZE} people in the group. Go back and add $missing."
    }

    fun initiate() {
        viewModelScope.launch(Dispatchers.IO) {
            val members = memberPublicKeys.mapNotNull { publicKey ->
                nostrRepository.getProfileWithPublicKey(publicKey)
            }

            selectChatRoomTypeUIState = SelectChatRoomTypeUIState.Loaded(
                members = members
            )
        }
    }

    fun selectChatRoomType(chatRoomType: ChatRoomType) {
        // Once the room exists the choice is baked into its epoch-0 group context and
        // re-picking here would change nothing.
        if (isActionPending.value || createdChatRoomId.value != null) return

        // Belt and braces alongside the disabled card: a group this small cannot carry
        // a meaningful threshold, so it never becomes robust.
        if (chatRoomType == ChatRoomType.ROBUST && !isRobustAvailable) return

        selectedChatRoomType.value = chatRoomType
    }

    /** Names the member behind [publicKey], falling back to the key itself. */
    fun displayNameFor(publicKey: HexKey): String {
        val members = (selectChatRoomTypeUIState as? SelectChatRoomTypeUIState.Loaded)?.members

        return members?.firstOrNull { it.publicKey == publicKey }?.humanReadableNameOrPubkey()
            ?: publicKey.take(SHORTENED_PUBLIC_KEY_LENGTH)
    }

    fun createChatRoom(
        onNavigateToRoute: (Route) -> Unit
    ) {
        if (isActionPending.value) return

        // The room survived a partial invite round — the user has read who was left out
        // and just wants to get on with the chat.
        createdChatRoomId.value?.let { chatRoomId ->
            onNavigateToRoute.invoke(
                ChatRoomMessagingRoute(
                    activeUserPublicKey = activeUserPublicKey,
                    chatRoomId = chatRoomId,
                    relayHint = null
                )
            )
            return
        }

        val nostrPrivateKey = activeWalletStateFlow.value?.business?.walletManager?.keyManager?.value?.nostrPrivateKey()
        if (nostrPrivateKey == null) {
            selectChatRoomTypeUIState = SelectChatRoomTypeUIState.Error(
                "Couldn't read your keys. Please try again."
            )
            return
        }

        isActionPending.value = true

        val keyPair = KeyPair(
            privKey = nostrPrivateKey.value.toByteArray()
        )

        val gid = RandomInstance.bytes(32).toHexKey() // TODO: Generate GID through frost...

        // Convenient rooms keep the creator as the lone admin; robust rooms hand
        // everyone the same authority. This is the whole behavioural difference
        // between the two — the inbound path derives Participant.adminAt from
        // exactly this list.
        // TODO: Robust rooms still need the t-of-n approval itself, i.e. FROST
        //  signing over admin changes. Today the list is set but every admin can
        //  still commit on their own.
        val adminPubkeys = when (selectedChatRoomType.value) {
            ChatRoomType.CONVENIENT -> listOf(keyPair.pubKey.toHexKey())
            ChatRoomType.ROBUST -> (listOf(keyPair.pubKey.toHexKey()) + memberPublicKeys).distinct()
        }

        // Stamp initial metadata via the shared factory so UI + CLI stay
        // byte-identical. Bake the MarmotGroupData extension into the
        // epoch-0 GroupContext directly (see `MarmotManager.createGroup`)
        // so later invitees receive a pre-populated group from the
        // welcome and never have to chase an undecryptable bootstrap
        // commit that predates their membership.
        val metadata = MarmotGroupData.bootstrap(
            nostrGroupId = gid,
            creatorPubKey = keyPair.pubKey.toHexKey(),
            outboxRelays = Relays.DefaultDMRelayList.map { it.url },
            name = name,
            description = description.orEmpty()
        ).copy(
            adminPubkeys = adminPubkeys
        )

        val extras = listOf(metadata.toExtension())

        val signingKeyPair = Ed25519.generateKeyPair()
//      TODO: Use nostrPrivateKey  Ed25519.publicFromPrivate(privateKey).let { ed25519pubKey ->
//            Ed25519KeyPair(privateKey, ed25519pubKey)
//        }
        val group = MlsGroup.create(keyPair.pubKey, signingKeyPair.privateKey, extras)

        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getOrCreateChatRoom(
                // Key the room on the Marmot `nostr_group_id` baked into the group's
                // 0xF2EE extension, NOT on `MlsGroup`'s own randomly generated groupId —
                // they are unrelated 32-byte values. GroupEvents are h-tagged with the
                // room id, while every inbound path (and the invitee's own join) resolves
                // rooms by nostrGroupId, so using the MLS id meant the two sides could
                // never see each other's events.
                chatRoomId = gid,
                activeUserPublicKey = keyPair.pubKey.toHexKey(),
                relayHint = null,
                defaultSubject = name,
                description = description,
                mlsGroupState = group.saveState().encodeTls().toHex()
            )

            if (localChatRoom == null) {
                withContext(Dispatchers.Main) {
                    isActionPending.value = false
                    onNavigateToRoute.invoke(
                        ImplementationPendingRoute("Something went wrong")
                    )
                }
                return@launch
            }

            createdChatRoomId.value = localChatRoom.chatRoom.id

            val notAdded = inviteMembers(chatRoomId = localChatRoom.chatRoom.id)

            withContext(Dispatchers.Main) {
                isActionPending.value = false

                if (notAdded.isEmpty()) {
                    onNavigateToRoute.invoke(
                        ChatRoomMessagingRoute(
                            activeUserPublicKey = keyPair.pubKey.toHexKey(),
                            chatRoomId = localChatRoom.chatRoom.id,
                            relayHint = null
                        )
                    )
                } else {
                    // The group is real and usable without them; say who is missing instead
                    // of dropping the user into a chat that is quietly short a few people.
                    membersNotAdded.clear()
                    membersNotAdded.addAll(notAdded)
                }
            }
        }
    }

    /**
     * Adds every picked member to the freshly created group and returns the ones that
     * could not be added.
     */
    private suspend fun inviteMembers(chatRoomId: String): List<HexKey> {
        if (memberPublicKeys.isEmpty()) return emptyList()

        // Resolve the key packages concurrently so the whole batch costs one relay round
        // trip rather than one per member.
        val keyPackages = coroutineScope {
            memberPublicKeys.map { publicKey ->
                async {
                    publicKey to withTimeoutOrNull(KEY_PACKAGE_LOOKUP_TIMEOUT) {
                        chatRepository.observeMarmotKeyPackageForPublicKey(publicKey)
                            .filterNotNull()
                            .first()
                    }
                }
            }.awaitAll()
        }

        val notAdded = mutableListOf<HexKey>()

        keyPackages.forEach { (publicKey, marmotKeyPackage) ->
            if (marmotKeyPackage == null) {
                logger.w("No key package for $publicKey; leaving them out of $chatRoomId")
                notAdded.add(publicKey)
                return@forEach
            }

            // Re-read the room before each invite: `inviteMember` advances the MLS epoch and
            // persists the new state, so reusing the snapshot taken before the previous
            // invite would build this commit on top of state the group has already left.
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)
            if (localChatRoom == null) {
                logger.e("Chat room $chatRoomId disappeared mid-invite")
                notAdded.add(publicKey)
                return@forEach
            }

            runCatching {
                chatRepository.inviteMember(
                    localChatRoom = localChatRoom,
                    peerPublicKey = publicKey,
                    peerKeyPackage = marmotKeyPackage
                )
            }.onFailure { throwable ->
                logger.e("Failed to invite $publicKey to $chatRoomId", throwable)
                notAdded.add(publicKey)
            }
        }

        return notAdded
    }

    companion object {
        private const val TAG = "SelectChatRoomTypeViewModel"

        /** Shared budget for pulling every picked member's key package off the relays. */
        private val KEY_PACKAGE_LOOKUP_TIMEOUT = 20.seconds

        /** Enough of a pubkey to tell two members apart when no profile is stored. */
        private const val SHORTENED_PUBLIC_KEY_LENGTH = 12

        fun factory(
            activeUserPublicKey: HexKey,
            name: String,
            description: String?,
            memberPublicKeys: List<HexKey>,
            initialSelectChatRoomTypeUIState: SelectChatRoomTypeUIState = SelectChatRoomTypeUIState.Loading,
            activeWalletStateFlow: StateFlow<ActiveWallet?>,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SelectChatRoomTypeViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    name = name,
                    description = description,
                    memberPublicKeys = memberPublicKeys,
                    initialSelectChatRoomTypeUIState = initialSelectChatRoomTypeUIState,
                    activeWalletStateFlow = activeWalletStateFlow,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}
