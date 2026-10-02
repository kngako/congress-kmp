package press.mantra.compose.ui.view.model

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
                )
            }
        }
    }

    private suspend fun peopleBehind(publicKeys: Set<HexKey>): Map<HexKey, Profile> =
        publicKeys.mapNotNull { publicKey ->
            nostrRepository.getProfileWithPublicKey(publicKey)?.let { publicKey to it }
        }.toMap()

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
                )
            }
        }
    }
}
