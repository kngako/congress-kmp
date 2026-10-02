package press.mantra.compose.ui.view.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.nostr.nip30303.ProjectArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ProjectEvent
import press.mantra.compose.nostr.nip30303.ProjectTranslationArtifactVersionEvent
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.view.state.AddProjectUIState

class AddProjectViewModel(
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialAddProjectUIState: AddProjectUIState,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository,
    val mantraRepository: MantraRepository,
    val frostSigningRepository: FrostSigningRepository,
): ViewModel() {

    var addProjectUIState: AddProjectUIState by mutableStateOf(initialAddProjectUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateAddProject() {
        logger.d("initiateAddProject: $chatRoomId")
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)

            addProjectUIState = if (localChatRoom == null) {
                AddProjectUIState.Error("Couldn't find the chat room")
            } else {
                AddProjectUIState.Loaded(
                    localChatRoom = localChatRoom,
                    sourceVersions = sourceVersionsOf(localChatRoom.chatRoom.id),
                    translations = translationsOf(localChatRoom.chatRoom.id),
                    canSign = frostSigningRepository.canSign(chatRoomId),
                )
            }
        }
    }

    /**
     * Every version in the room's library, walked down from its artifacts.
     *
     * There is no by-room query for versions -- a version hangs off an artifact,
     * which is the same reason the chronicle walks the tree rather than querying
     * per kind. A room's library is small and this runs once when the form
     * opens.
     */
    private suspend fun sourceVersionsOf(chatRoomId: String) =
        mantraRepository.getArtifacts(chatRoomId)
            .flatMap { mantraRepository.getArtifactVersions(it.id) }

    private suspend fun translationsOf(chatRoomId: String) =
        sourceVersionsOf(chatRoomId)
            .flatMap { mantraRepository.getTranslationsForArtifactVersion(it.id) }

    /**
     * Asks the group to sign a project into being, with whatever it starts with.
     *
     * The project is not created here and does not exist yet, the way an
     * artifact does not: what goes out is a proposal, and the project appears on
     * every member's device at once -- authored by the room's own key -- when
     * enough members have signed.
     *
     * One session over the project and its first contents. The memberships
     * cannot be built by this screen because each carries the project's id,
     * which is a hash over the group's key at the room's derivation path: it
     * does not exist until the proposal is made. So the project leads and the
     * memberships are derived from it, the same shape an artifact and its first
     * version use.
     *
     * All-or-nothing, which is right. A project is a statement about which work
     * belongs together, and half of that statement signed is not a smaller
     * version of it.
     *
     * [creatorPublicKey] is this member and is not asked for. [ownerPublicKeys]
     * is asked for and defaults to the room, and must not be empty -- the event
     * builder refuses one, and the form keeps the last entry from being removed
     * rather than letting it get that far.
     */
    fun addProject(
        localChatRoom: LocalChatRoom,
        nameField: TextFieldState,
        descriptionField: TextFieldState,
        ownerPublicKeys: Set<HexKey>,
        sourceVersionIds: List<HexKey>,
        translationIds: List<HexKey>,
        onSuccess: (sessionId: String) -> Unit,
        onFailure: () -> Unit
    ) {
        val name = nameField.text.toString()
        val description = descriptionField.text.toString()

        if (name.isBlank() || ownerPublicKeys.isEmpty()) {
            onFailure.invoke()
            return
        }

        // Guard against double submits from repeated FAB taps.
        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val projectEventTemplate = ProjectEvent.build(
                name = name,
                description = description,
                creatorPublicKey = activeUserPublicKey,
                ownerPublicKeys = ownerPublicKeys,
            )

            val session = runCatching {
                frostSigningRepository.proposeSigningBatch(
                    localChatRoom = localChatRoom,
                    userPublicKey = activeUserPublicKey,
                    lead = projectEventTemplate,
                    dependents = { project ->
                        buildList<EventTemplate<*>> {
                            addAll(
                                ProjectArtifactVersionEvent.membershipsOf(
                                    project = project,
                                    artifactVersionIds = sourceVersionIds,
                                )
                            )
                            addAll(
                                ProjectTranslationArtifactVersionEvent.membershipsOf(
                                    project = project,
                                    translationArtifactVersionIds = translationIds,
                                )
                            )
                        }
                    }
                )
            }.onFailure { error ->
                logger.e("Failed to propose a project for signing", error)
            }.getOrNull()

            if (session != null) {
                nameField.clearText()
                descriptionField.clearText()

                viewModelScope.launch(Dispatchers.Main) {
                    onSuccess.invoke(session.id)
                }
            } else {
                viewModelScope.launch(Dispatchers.Main) {
                    onFailure.invoke()
                }
            }

            isActionPending.value = false
        }
    }

    companion object {
        private const val TAG = "AddProjectViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            chatRoomId: String,
            relayHint: String?,
            initialAddProjectUIState: AddProjectUIState = AddProjectUIState.Loading,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository,
            mantraRepository: MantraRepository,
            frostSigningRepository: FrostSigningRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddProjectViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialAddProjectUIState = initialAddProjectUIState,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository,
                    mantraRepository = mantraRepository,
                    frostSigningRepository = frostSigningRepository
                )
            }
        }
    }
}
