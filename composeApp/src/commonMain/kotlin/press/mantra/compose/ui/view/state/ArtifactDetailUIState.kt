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
    ): ArtifactDetailUIState

    data class Error(
        val message: String
    ): ArtifactDetailUIState

    data object Loading: ArtifactDetailUIState
}
