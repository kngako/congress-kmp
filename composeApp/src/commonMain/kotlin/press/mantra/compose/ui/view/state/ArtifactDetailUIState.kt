package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraChapter
import press.mantra.compose.database.model.MantraTranslationArtifactVersion

sealed interface ArtifactDetailUIState {
    data class Loaded(
        val artifact: MantraArtifact,
        val versions: List<MantraArtifactVersion> = emptyList(),
        val chapters: List<MantraChapter> = emptyList(),
        val translations: List<MantraTranslationArtifactVersion> = emptyList(),

        /**
         * Whether this device could open a subgroup for this artifact at all.
         *
         * The same two conditions `ChatRoomDetailUIState.canAddSubgroup` states,
         * and both are about capability rather than permission: the room has to
         * be a Marmot one, since a NIP-17 room has no admins to be a parent's,
         * and this device has to hold a share of the group's key, because
         * certifying a child is a signature by the parent and
         * `FrostSigningManager.proposeSigningBatch` throws for a device that
         * cannot make one.
         *
         * The first flag on this state, which is why it arrives with the
         * subgroup button rather than before it.
         */
        val canAddSubgroup: Boolean = false,
    ): ArtifactDetailUIState

    data class Error(
        val message: String
    ): ArtifactDetailUIState

    data object Loading: ArtifactDetailUIState
}
