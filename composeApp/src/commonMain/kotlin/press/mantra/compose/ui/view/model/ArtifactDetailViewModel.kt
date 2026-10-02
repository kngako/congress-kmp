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
import press.mantra.compose.database.model.traits.SubgroupOriginEntity
import press.mantra.compose.managers.SubgroupOrigin
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.view.state.ArtifactDetailUIState

class ArtifactDetailViewModel(
    val artifactId: String,
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialArtifactDetailUIState: ArtifactDetailUIState,
    val mantraRepository: MantraRepository,
    val chatRepository: ChatRepository,
): ViewModel() {

    var artifactDetailUIState: ArtifactDetailUIState by mutableStateOf(initialArtifactDetailUIState)
        private set

    private val logger = Logger.withTag(TAG)

    fun initiateArtifactDetail() {
        logger.d("initiateArtifactDetail: $artifactId")
        viewModelScope.launch(Dispatchers.IO) {
            val artifact = mantraRepository.getArtifact(artifactId)

            artifactDetailUIState = if (artifact == null) {
                ArtifactDetailUIState.Error("Couldn't find the artifact")
            } else {
                ArtifactDetailUIState.Loaded(
                    artifact = artifact,
                    versions = mantraRepository.getArtifactVersions(artifactId),
                    chapters = mantraRepository.getChaptersForArtifact(artifactId),
                    translations = mantraRepository.getTranslationsForArtifact(artifactId),
                    canAddSubgroup = canAddSubgroupFor(artifact),
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

        return room?.chatRoom?.mlsGroupState != null &&
            chatRepository.canSign(parentChatRoomId)
    }

    companion object {
        private const val TAG = "ArtifactDetailViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            artifactId: String,
            chatRoomId: String,
            relayHint: String?,
            initialArtifactDetailUIState: ArtifactDetailUIState = ArtifactDetailUIState.Loading,
            mantraRepository: MantraRepository,
            chatRepository: ChatRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ArtifactDetailViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    artifactId = artifactId,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialArtifactDetailUIState = initialArtifactDetailUIState,
                    mantraRepository = mantraRepository,
                    chatRepository = chatRepository,
                )
            }
        }
    }
}
