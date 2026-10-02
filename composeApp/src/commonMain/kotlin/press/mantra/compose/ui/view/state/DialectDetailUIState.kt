package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraTranslationArtifactVersion

sealed interface DialectDetailUIState {
    data class Loaded(
        val dialect: MantraDialect,

        /**
         * The artifacts written in this dialect.
         *
         * This and [translations] are what the screen is for. A dialect on its
         * own is three strings -- a name, a language code and a country -- and a
         * screen showing only those has no reason to exist. These are the two
         * tables that carry a `dialectId`, and between them they answer the only
         * question a member opens a dialect to ask.
         */
        val artifacts: List<MantraArtifact> = emptyList(),

        /** The translations into this dialect. */
        val translations: List<MantraTranslationArtifactVersion> = emptyList(),

        /**
         * Whether this device could open a subgroup for this dialect at all.
         *
         * Two conditions, both about capability rather than permission, and the
         * same two `ChatRoomDetailUIState.canAddSubgroup` states: the room has to
         * be a Marmot one, since a NIP-17 room has no admins to be a parent's,
         * and this device has to hold a share of the group's key, because
         * certifying a child is a signature by the parent and
         * `FrostSigningManager.proposeSigningBatch` throws for a device that
         * cannot make one.
         *
         * The rest of the refusals are the picker's, where they can name who is
         * missing and what to do about it.
         */
        val canAddSubgroup: Boolean = false,
    ): DialectDetailUIState

    data class Error(
        val message: String
    ): DialectDetailUIState

    data object Loading: DialectDetailUIState
}
