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
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.navigation.routes.HomeRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.view.state.ChatRoomDetailUIState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.managers.SubgroupManager
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.ProjectEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import press.mantra.compose.database.model.types.MarmotReindexReport

class ChatRoomDetailViewModel(
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialChatRoomDetailUIState: ChatRoomDetailUIState,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository,
    val mantraRepository: MantraRepository,
): ViewModel() {

    var chatRoomDetailUIState: ChatRoomDetailUIState by mutableStateOf(initialChatRoomDetailUIState)
        private set

    /**
     * Where the room's replay has got to, for the button that started it.
     *
     * Held here rather than in [ChatRoomDetailUIState] because a replay does not
     * change what the screen is showing -- the message list is observed elsewhere
     * and updates itself -- it only changes what the button has to say for itself.
     */
    var reindexState: ReindexState by mutableStateOf(ReindexState.Idle)
        private set

    sealed interface ReindexState {
        data object Idle : ReindexState

        data object Running : ReindexState

        data class Done(val report: MarmotReindexReport) : ReindexState

        data class Failed(val message: String) : ReindexState
    }



    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateChatRoomDetail() {
        logger.d("compressed (most likely chat room): $chatRoomId")
        // Get ChatRoomById
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)

            logger.d("localChatRoom: $localChatRoom")
            chatRoomDetailUIState = if (localChatRoom == null) {
                // Initiate new chat... and navigate to new chat...
                ChatRoomDetailUIState.Error("Missing chat room")
            } else {
                // Read into a local so the names are resolved from exactly the
                // list the cards are drawn from. Resolving inside the argument
                // list would make the two depend on evaluation order.
                val subgroups = chatRepository.subgroupsOf(chatRoomId)

                ChatRoomDetailUIState.Loaded(
                    localChatRoom = localChatRoom,
                    artifacts = mantraRepository.getArtifacts(chatRoomId),
                    dialects = mantraRepository.getDialects(chatRoomId),
                    projects = mantraRepository.getProjects(chatRoomId),
                    groupKeyState = chatRepository.groupKeyState(chatRoomId),
                    signedGroupKeyStateEvent = chatRepository.signedGroupKeyStateEvent(chatRoomId),
                    subgroups = subgroups,
                    subgroupOriginNames = originNamesIn(subgroups),
                    parents = chatRepository.parentsOf(chatRoomId),
                    canAddSubgroup = localChatRoom.chatRoom.mlsGroupState != null &&
                        chatRepository.canSign(chatRoomId),
                    adoptionOffers = chatRepository.adoptionOffersFor(chatRoomId),
                    // A key state as well as a share: the certificate carries
                    // this group's threshold key, and the acceptance re-announces
                    // that very state, so a room that has agreed nothing has
                    // neither half to work with.
                    canAddParent = localChatRoom.chatRoom.mlsGroupState != null &&
                        chatRepository.groupKeyState(chatRoomId) != null &&
                        chatRepository.canSign(chatRoomId),
                )
            }
        }
    }

    /**
     * What each subgroup's origin is called, where this device holds the row.
     *
     * Only the rows the certificates actually name, and only from this device's
     * own tables. An entity that is not here is left out, and the card shows a
     * short hash -- which is truthful rather than a gap, because a certificate is
     * allowed to name something the reader has never seen: these entities live in
     * the parent's tables, and a parent member reading a certificate may hold the
     * room without holding the row.
     */
    private suspend fun originNamesIn(
        subgroups: List<SubgroupManager.Subgroup>
    ): Map<HexKey, String> = buildMap {
        subgroups.mapNotNull { it.origin }.forEach { origin ->
            if (containsKey(origin.id)) return@forEach

            val name = when (origin.kind) {
                DialectEvent.KIND -> mantraRepository.getDialect(origin.id)?.name
                ArtifactEvent.KIND -> mantraRepository.getArtifact(origin.id)?.name
                TranslationArtifactVersionEvent.KIND ->
                    mantraRepository.getTranslation(origin.id)?.name
                ProjectEvent.KIND -> mantraRepository.getProject(origin.id)?.name
                else -> null
            }

            name?.let { put(origin.id, it) }
        }
    }

    /**
     * Asks this group's quorum to accept [parentChatRoomId] as a parent.
     *
     * The acceptance, and the half that decides: the certificate is an offer
     * until a quorum of *this* group has re-announced its key state naming them.
     * `SubgroupManager.proposeAcceptance` carries the whole resulting set, so a
     * group that already has a parent keeps it.
     *
     * [onDone] is called with null on success and a message otherwise, so the
     * caller can show it from a scope that outlives this screen.
     */
    fun acceptParent(parentChatRoomId: HexKey, onDone: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val accepted = chatRepository.acceptParent(
                chatRoomId = chatRoomId,
                parentChatRoomId = parentChatRoomId,
                userPublicKey = activeUserPublicKey
            )

            if (!accepted) {
                logger.e("Failed to ask $chatRoomId to accept $parentChatRoomId")
                // On the main thread, because a caller's callback may touch
                // things an IO coroutine must not -- navigation most of all. See
                // `SelectAdoptingGroupViewModel.finish`, where calling back from
                // IO crashed outright.
                withContext(Dispatchers.Main) {
                    onDone("Couldn't ask this group to accept that parent. Please try again.")
                }
                return@launch
            }

            // The offer leaves the list only when the state is signed and filed,
            // which is a quorum away -- so this reloads rather than crossing it
            // off, and the card stays until the group has actually agreed.
            initiateChatRoomDetail()
            withContext(Dispatchers.Main) { onDone(null) }
        }
    }

    fun leaveGroup(
        localChatRoom: LocalChatRoom,
        onPopBackToRoute: (Route) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.leaveChatRoom(
                localChatRoom,
                onCompletion = {
                    viewModelScope.launch(Dispatchers.Main) {
                        onPopBackToRoute.invoke(
                            HomeRoute(
                                activeUserPublicKey = activeUserPublicKey,
                            )
                        )
                    }
                }
            )
        }
    }

    /**
     * Reads the room's stored marmot group events again.
     *
     * Nostr relays impose no ordering, so a group event can arrive before the group
     * can read it -- a message from an epoch whose commit has not landed, a commit
     * for an epoch ahead of the local one. Nothing revisits those once the gap
     * fills, which is what this is for.
     *
     * Only events with nothing to show for them are replayed, so pressing this on a
     * room where nothing went wrong changes nothing. The feed is observed, so
     * anything recovered appears in the message list without a refresh.
     */
    fun reindexMarmotGroupEvents() {
        if (reindexState is ReindexState.Running) return

        reindexState = ReindexState.Running
        viewModelScope.launch(Dispatchers.IO) {
            reindexState = try {
                ReindexState.Done(
                    chatRepository.reindexMarmotGroupEvents(
                        chatRoomId = chatRoomId,
                        userPublicKey = activeUserPublicKey,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.e("Failed to reindex $chatRoomId", e)
                ReindexState.Failed(e.message ?: "Reindex failed")
            }
        }
    }

    fun softDeleteGroup(
        localChatRoom: LocalChatRoom,
        onPopBackToRoute: (Route) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.softDeleteChatRoom(
                chatRoom = localChatRoom.chatRoom,
                onCompletion = {
                    viewModelScope.launch(Dispatchers.Main) {
                        onPopBackToRoute.invoke(
                            HomeRoute(
                                activeUserPublicKey = activeUserPublicKey,
                            )
                        )
                    }
                }
            )
        }
    }


    companion object {
        private const val TAG = "ChatRoomDetailViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            chatRoomId: String,
            relayHint: String?,
            initialChatRoomDetailUIState: ChatRoomDetailUIState = ChatRoomDetailUIState.Loading,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository,
            mantraRepository: MantraRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatRoomDetailViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialChatRoomDetailUIState = initialChatRoomDetailUIState,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository,
                    mantraRepository = mantraRepository,
                )
            }
        }
    }
}