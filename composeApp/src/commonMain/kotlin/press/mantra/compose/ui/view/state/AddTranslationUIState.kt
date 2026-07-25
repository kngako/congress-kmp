package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraDialect

sealed interface AddTranslationUIState {
    data class Loaded(
        val artifact: MantraArtifact,
        val dialects: List<MantraDialect> = emptyList(),
    ): AddTranslationUIState

    data class Error(
        val message: String
    ): AddTranslationUIState

    data object Loading: AddTranslationUIState
}
