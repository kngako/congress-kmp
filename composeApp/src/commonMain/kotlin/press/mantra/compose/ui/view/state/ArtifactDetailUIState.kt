package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.managers.SubgroupManager
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
        /**
         * The rooms this group has made for this artifact.
         *
         * The other direction of a birth certificate, and the reason the
         * certificate carries an origin at all. Empty says only that this device
         * holds no certificate naming it -- a room made for it by a group this
         * device is not in is invisible here, as everywhere else.
         */
        val subgroups: List<SubgroupManager.Subgroup> = emptyList(),

        /**
         * Key ceremonies already under way for this artifact.
         *
         * Shown rather than refused. Two admins opening this screen at the same
         * time both see no room and both start one, which is the race the whole
         * pair exists for -- the screen that invites the act is the screen that
         * cannot otherwise see the act in progress. A group may still want two
         * rooms for one artifact, and that is the group's business.
         */
        val subgroupCeremonies: List<DkgSession> = emptyList(),

    ): ArtifactDetailUIState

    data class Error(
        val message: String
    ): ArtifactDetailUIState

    data object Loading: ArtifactDetailUIState
}
