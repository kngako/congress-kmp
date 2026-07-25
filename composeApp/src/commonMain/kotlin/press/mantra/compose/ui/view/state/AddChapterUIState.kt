package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MantraArtifact

sealed interface AddChapterUIState {
    data class Loaded(
        val artifact: MantraArtifact
    ): AddChapterUIState

    data class Error(
        val message: String
    ): AddChapterUIState

    data object Loading: AddChapterUIState
}
