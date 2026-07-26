package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MantraChunk

sealed interface TranslateChunkUIState {
    data class Loaded(
        val originalChunk: MantraChunk,
        val existingTranslationText: String = "",
    ): TranslateChunkUIState

    data class Error(
        val message: String
    ): TranslateChunkUIState

    data object Loading: TranslateChunkUIState
}
