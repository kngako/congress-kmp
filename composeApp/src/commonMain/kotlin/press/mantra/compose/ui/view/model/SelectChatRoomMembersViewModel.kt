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
import press.mantra.compose.database.model.NegentropySynchronizeRequest
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.types.SynchronizationFilter
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.Relays
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.navigation.routes.ChatRoomMessagingRoute
import press.mantra.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.view.state.SelectChatRoomMembersUIState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
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
 * Picks the members of a brand new group chat and, once the user confirms,
 * creates the MLS group and invites everyone that was ticked.
 *
 * Only profiles already in the local store are offered — this screen is a
 * picker, not a directory search.
 */
class SelectChatRoomMembersViewModel(
    val activeUserPublicKey: HexKey,
    val name: String,
    val description: String?,
    initialSelectChatRoomMembersUIState: SelectChatRoomMembersUIState,
    val activeWalletStateFlow: StateFlow<ActiveWallet?>,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository,
): ViewModel() {

    var selectChatRoomMembersUIState: SelectChatRoomMembersUIState by mutableStateOf(initialSelectChatRoomMembersUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    /** Public keys the user has ticked, in the order they were ticked. */
    val selectedPublicKeys = mutableStateListOf<HexKey>()

    /**
     * Set as soon as the room exists. Creating a group is not idempotent, so a second
     * tap after a partially failed invite round has to reuse this room rather than
     * mint another one.
     */
    val createdChatRoomId: MutableState<String?> = mutableStateOf(null)

    /** Members the group was created without, because no key package ever showed up. */
    val membersNotAdded = mutableStateListOf<Profile>()

    fun initiate() {
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = nostrRepository.searchableProfiles(
                excludingPublicKeys = arrayOf(activeUserPublicKey)
            )

            selectChatRoomMembersUIState = SelectChatRoomMembersUIState.Loaded(
                profiles = profiles
            )

            // Ask for everybody's key package up front: by the time the user has finished
            // ticking names the relay round trip is usually done, so creating the group
            // doesn't have to sit and wait for it.
            scheduleKeyPackageEventSynchronization(profiles)
        }
    }

    fun toggleMember(publicKey: HexKey) {
        // Once the room exists the picker is spent — further ticks would silently do
        // nothing, since creation (and with it the invite round) has already run.
        if (isActionPending.value || createdChatRoomId.value != null) return

        if (!selectedPublicKeys.remove(publicKey)) {
            selectedPublicKeys.add(publicKey)
        }
    }

    fun isSelected(publicKey: HexKey): Boolean = selectedPublicKeys.contains(publicKey)

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
            selectChatRoomMembersUIState = SelectChatRoomMembersUIState.Error(
                "Couldn't read your keys. Please try again."
            )
            return
        }

        isActionPending.value = true

        val keyPair = KeyPair(
            privKey = nostrPrivateKey.value.toByteArray()
        )

        val gid = RandomInstance.bytes(32).toHexKey() // TODO: Generate GID through frost...

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

            val notAdded = inviteSelectedMembers(chatRoomId = localChatRoom.chatRoom.id)

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
     * Adds every ticked profile to the freshly created group and returns the ones that
     * could not be added.
     */
    private suspend fun inviteSelectedMembers(chatRoomId: String): List<Profile> {
        val selectedProfiles = selectedProfiles()
        if (selectedProfiles.isEmpty()) return emptyList()

        // Resolve the key packages concurrently so the whole batch costs one relay round
        // trip rather than one per member.
        val keyPackages = coroutineScope {
            selectedProfiles.map { profile ->
                async {
                    profile to withTimeoutOrNull(KEY_PACKAGE_LOOKUP_TIMEOUT) {
                        chatRepository.observeMarmotKeyPackageForPublicKey(profile.publicKey)
                            .filterNotNull()
                            .first()
                    }
                }
            }.awaitAll()
        }

        val notAdded = mutableListOf<Profile>()

        keyPackages.forEach { (profile, marmotKeyPackage) ->
            if (marmotKeyPackage == null) {
                logger.w("No key package for ${profile.publicKey}; leaving them out of $chatRoomId")
                notAdded.add(profile)
                return@forEach
            }

            // Re-read the room before each invite: `inviteMember` advances the MLS epoch and
            // persists the new state, so reusing the snapshot taken before the previous
            // invite would build this commit on top of state the group has already left.
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)
            if (localChatRoom == null) {
                logger.e("Chat room $chatRoomId disappeared mid-invite")
                notAdded.add(profile)
                return@forEach
            }

            runCatching {
                chatRepository.inviteMember(
                    localChatRoom = localChatRoom,
                    peerPublicKey = profile.publicKey,
                    peerKeyPackage = marmotKeyPackage
                )
            }.onFailure { throwable ->
                logger.e("Failed to invite ${profile.publicKey} to $chatRoomId", throwable)
                notAdded.add(profile)
            }
        }

        return notAdded
    }

    private fun selectedProfiles(): List<Profile> {
        val profiles = (selectChatRoomMembersUIState as? SelectChatRoomMembersUIState.Loaded)?.profiles
            ?: return emptyList()

        return profiles.filter { selectedPublicKeys.contains(it.publicKey) }
    }

    private suspend fun scheduleKeyPackageEventSynchronization(profiles: List<Profile>) {
        if (profiles.isEmpty()) return

        // TODO: Sync from aggregated relays of profile KeyPackageRelays...
        val synchronizationFilter = SynchronizationFilter(
            authors = profiles.map { it.publicKey }.toTypedArray(),
            kinds = arrayOf(
                KeyPackageEvent.KIND
            )
        )

        nostrRepository.queueNegentropySynchronizeRequest(
            Relays.DefaultDMRelayList.map { normalizedRelayUrl ->
                NegentropySynchronizeRequest(
                    id = NegentropySynchronizeRequest.computeId(
                        relayURL = normalizedRelayUrl.url,
                        synchronizationFilter = synchronizationFilter
                    ),
                    purpose = "key-packages",
                    synchronizationFilter = synchronizationFilter,
                    relayURL = normalizedRelayUrl.url,
                    level = 0
                )
            }
        )
    }

    companion object {
        private const val TAG = "SelectChatRoomMembersViewModel"

        /** Shared budget for pulling every selected member's key package off the relays. */
        private val KEY_PACKAGE_LOOKUP_TIMEOUT = 20.seconds

        fun factory(
            activeUserPublicKey: HexKey,
            name: String,
            description: String?,
            initialSelectChatRoomMembersUIState: SelectChatRoomMembersUIState = SelectChatRoomMembersUIState.Loading,
            activeWalletStateFlow: StateFlow<ActiveWallet?>,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SelectChatRoomMembersViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    name = name,
                    description = description,
                    initialSelectChatRoomMembersUIState = initialSelectChatRoomMembersUIState,
                    activeWalletStateFlow = activeWalletStateFlow,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}
