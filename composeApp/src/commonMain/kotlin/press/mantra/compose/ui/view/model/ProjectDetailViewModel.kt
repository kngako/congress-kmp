package press.mantra.compose.ui.view.model

import press.mantra.compose.managers.SubgroupManager
import press.mantra.compose.database.model.DkgSession
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
import press.mantra.compose.database.model.Profile
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.database.model.traits.SubgroupOriginEntity
import press.mantra.compose.managers.SubgroupOrigin
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.view.state.ProjectDetailUIState

class ProjectDetailViewModel(
    val projectId: String,
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialProjectDetailUIState: ProjectDetailUIState,
    val mantraRepository: MantraRepository,
    val nostrRepository: NostrRepository,
    val frostSigningRepository: FrostSigningRepository,
    val chatRepository: ChatRepository,
): ViewModel() {

    var projectDetailUIState: ProjectDetailUIState by mutableStateOf(initialProjectDetailUIState)
        private set

    private val logger = Logger.withTag(TAG)

    fun initiateProjectDetail() {
        logger.d("initiateProjectDetail: $projectId")
        viewModelScope.launch(Dispatchers.IO) {
            val project = mantraRepository.getProject(projectId)

            projectDetailUIState = if (project == null) {
                ProjectDetailUIState.Error("Couldn't find the project")
            } else {
                // Read before the state is built so both halves come from one
                // call, and so the room is resolved once rather than twice.
                val (madeFor, ceremoniesFor) = subgroupsAndCeremoniesFor(project)

                ProjectDetailUIState.Loaded(
                    project = project,
                    sourceVersions = mantraRepository.getProjectArtifactVersions(projectId),
                    translations = mantraRepository.getProjectTranslationArtifactVersions(projectId),
                    // The creator and the owners, resolved once here. A screen
                    // cannot await a database per row, and these are a handful
                    // of keys read together.
                    people = peopleBehind(
                        project.ownerPublicKeySet() + project.creatorPublicKey
                    ),
                    canSign = frostSigningRepository.canSign(chatRoomId),
                    canAddSubgroup = canAddSubgroupFor(project),
                    subgroups = madeFor,
                    subgroupCeremonies = ceremoniesFor,
                )
            }
        }
    }


    /**
     * Whether a subgroup could be opened for [entity] at all.
     *
     * The two conditions `ChatRoomDetailUIState.canAddSubgroup` states, read off
     * the entity's *own* room rather than the route's: the parent of a subgroup
     * made here is the room the entity actually lives in, and
     * `SubgroupOrigin.of` is the one place that says which room that is.
     */
    private suspend fun canAddSubgroupFor(entity: SubgroupOriginEntity): Boolean {
        val parentChatRoomId = SubgroupOrigin.of(entity).parentChatRoomId
        val room = chatRepository.getChatRoomByIdentifier(parentChatRoomId)

        return room?.mlsGroupStateOf(activeUserPublicKey) != null &&
            chatRepository.canSign(parentChatRoomId)
    }

    private suspend fun peopleBehind(publicKeys: Set<HexKey>): Map<HexKey, Profile> =
        publicKeys.mapNotNull { publicKey ->
            nostrRepository.getProfileWithPublicKey(publicKey)?.let { publicKey to it }
        }.toMap()


    /**
     * The rooms made for [entity] and the ceremonies under way for it.
     *
     * Both off the entity's own room, which `SubgroupOrigin.of` is the one
     * authority on -- a subgroup made for this thing is a child of the room the
     * thing lives in, and asking any other room would answer about somebody
     * else's children.
     */
    private suspend fun subgroupsAndCeremoniesFor(
        entity: SubgroupOriginEntity
    ): Pair<List<SubgroupManager.Subgroup>, List<DkgSession>> {
        val candidate = SubgroupOrigin.of(entity)

        return chatRepository.subgroupsFor(candidate.parentChatRoomId, candidate.origin) to
            chatRepository.subgroupCeremoniesFor(candidate.parentChatRoomId, candidate.origin)
    }

    companion object {
        private const val TAG = "ProjectDetailViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            projectId: String,
            chatRoomId: String,
            relayHint: String?,
            initialProjectDetailUIState: ProjectDetailUIState = ProjectDetailUIState.Loading,
            mantraRepository: MantraRepository,
            nostrRepository: NostrRepository,
            frostSigningRepository: FrostSigningRepository,
            chatRepository: ChatRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProjectDetailViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    projectId = projectId,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialProjectDetailUIState = initialProjectDetailUIState,
                    mantraRepository = mantraRepository,
                    nostrRepository = nostrRepository,
                    frostSigningRepository = frostSigningRepository,
                    chatRepository = chatRepository,
                )
            }
        }
    }
}
