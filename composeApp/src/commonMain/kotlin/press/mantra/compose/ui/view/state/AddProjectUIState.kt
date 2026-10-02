package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface AddProjectUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,

        /** Everything in the room's library a project could be about. */
        val sourceVersions: List<MantraArtifactVersion> = emptyList(),

        /** And everything being translated. */
        val translations: List<MantraTranslationArtifactVersion> = emptyList(),

        /**
         * Whether this device holds a share of the group's key.
         *
         * A project is proposed, not created, so a device that cannot sign
         * cannot start one. Said on the form rather than discovered on the
         * button.
         */
        val canSign: Boolean = false,
    ): AddProjectUIState

    data class Error(
        val message: String
    ): AddProjectUIState

    data object Loading: AddProjectUIState
}
