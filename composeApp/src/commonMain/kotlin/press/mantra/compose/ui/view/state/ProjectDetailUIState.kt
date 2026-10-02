package press.mantra.compose.ui.view.state

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraProject
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.Profile

sealed interface ProjectDetailUIState {
    data class Loaded(
        val project: MantraProject,
        val sourceVersions: List<MantraArtifactVersion> = emptyList(),
        val translations: List<MantraTranslationArtifactVersion> = emptyList(),

        /**
         * The profiles behind the creator and the owners, where this device has
         * them.
         *
         * Resolved in the view model rather than looked up as each row draws:
         * the screen shows a handful of keys and a composable cannot await a
         * database. A key with no profile here is shown shortened, which is what
         * the rest of the app does with a stranger.
         */
        val people: Map<HexKey, Profile> = emptyMap(),

        /**
         * Whether this device holds a share of the group's key.
         *
         * Adding to a project is a proposal, and a device that cannot sign
         * cannot open one -- so the buttons say so rather than failing when
         * pressed.
         */
        val canSign: Boolean = false,
    ): ProjectDetailUIState

    data class Error(
        val message: String
    ): ProjectDetailUIState

    data object Loading: ProjectDetailUIState
}
