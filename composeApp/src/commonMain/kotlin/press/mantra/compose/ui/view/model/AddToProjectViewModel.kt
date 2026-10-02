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
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.nostr.nip30303.ProjectArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ProjectTranslationArtifactVersionEvent
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.view.state.AddToProjectUIState

/**
 * Adding one more piece of work to a project that already exists.
 *
 * The other half of `AddProjectViewModel`, and the simpler one: the project's
 * id is known, so there is no batch and no lead -- one event, one session.
 */
class AddToProjectViewModel(
    val projectId: String,
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val translations: Boolean,
    val relayHint: String?,
    initialAddToProjectUIState: AddToProjectUIState,
    val chatRepository: ChatRepository,
    val mantraRepository: MantraRepository,
    val frostSigningRepository: FrostSigningRepository,
): ViewModel() {

    var addToProjectUIState: AddToProjectUIState by mutableStateOf(initialAddToProjectUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateAddToProject() {
        logger.d("initiateAddToProject: $projectId translations=$translations")
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)
            val project = mantraRepository.getProject(projectId)

            addToProjectUIState = when {
                localChatRoom == null -> AddToProjectUIState.Error("Couldn't find the chat room")
                project == null -> AddToProjectUIState.Error("Couldn't find the project")
                else -> AddToProjectUIState.Loaded(
                    localChatRoom = localChatRoom,
                    project = project,
                    candidates = candidatesFor(localChatRoom.chatRoom.id),
                    canSign = frostSigningRepository.canSign(chatRoomId),
                )
            }
        }
    }

    /**
     * What the room has that the project does not already hold.
     *
     * Filtered rather than shown and refused: a duplicate membership is a
     * second event with its own id, so nothing downstream would stop it -- it
     * would just cost a quorum to say what the group has already said.
     */
    private suspend fun candidatesFor(chatRoomId: String): List<AddToProjectUIState.Candidate> {
        val versions = mantraRepository.getArtifacts(chatRoomId)
            .flatMap { mantraRepository.getArtifactVersions(it.id) }

        return if (translations) {
            val already = mantraRepository.getProjectTranslationArtifactVersions(projectId)
                .map { it.id }
                .toSet()

            versions
                .flatMap { mantraRepository.getTranslationsForArtifactVersion(it.id) }
                .filterNot { it.id in already }
                .map {
                    AddToProjectUIState.Candidate(
                        id = it.id,
                        label = it.name,
                        detail = null,
                    )
                }
        } else {
            val already = mantraRepository.getProjectArtifactVersions(projectId)
                .map { it.id }
                .toSet()

            versions
                .filterNot { it.id in already }
                .map { version ->
                    AddToProjectUIState.Candidate(
                        id = version.id,
                        label = version.versionLabel,
                        // The artifact, because a version label on its own is
                        // "1.0" and a room has several of those.
                        detail = mantraRepository.getArtifact(version.artifactId)?.name,
                    )
                }
        }
    }

    /**
     * Asks the group to sign one thing into the project.
     *
     * One event, so [FrostSigningRepository.proposeSigning]'s flat batch rather
     * than the lead-and-dependents form: this screen knows the project's id
     * because the project exists.
     */
    fun addToProject(
        localChatRoom: LocalChatRoom,
        selectedId: HexKey,
        onSuccess: (sessionId: String) -> Unit,
        onFailure: () -> Unit
    ) {
        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val template = if (translations) {
                ProjectTranslationArtifactVersionEvent.build(
                    projectId = projectId,
                    translationArtifactVersionId = selectedId,
                )
            } else {
                ProjectArtifactVersionEvent.build(
                    projectId = projectId,
                    artifactVersionId = selectedId,
                )
            }

            val session = runCatching {
                frostSigningRepository.proposeSigningBatch(
                    localChatRoom = localChatRoom,
                    userPublicKey = activeUserPublicKey,
                    events = listOf(template),
                )
            }.onFailure { error ->
                logger.e("Failed to propose adding to project $projectId", error)
            }.getOrNull()

            viewModelScope.launch(Dispatchers.Main) {
                if (session != null) onSuccess.invoke(session.id) else onFailure.invoke()
            }

            isActionPending.value = false
        }
    }

    companion object {
        private const val TAG = "AddToProjectViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            projectId: String,
            chatRoomId: String,
            translations: Boolean,
            relayHint: String?,
            initialAddToProjectUIState: AddToProjectUIState = AddToProjectUIState.Loading,
            chatRepository: ChatRepository,
            mantraRepository: MantraRepository,
            frostSigningRepository: FrostSigningRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddToProjectViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    projectId = projectId,
                    chatRoomId = chatRoomId,
                    translations = translations,
                    relayHint = relayHint,
                    initialAddToProjectUIState = initialAddToProjectUIState,
                    chatRepository = chatRepository,
                    mantraRepository = mantraRepository,
                    frostSigningRepository = frostSigningRepository,
                )
            }
        }
    }
}
