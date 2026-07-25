package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MantraChapter
import press.mantra.compose.database.model.MantraChunk

sealed interface ChapterDetailUIState {
    data class Loaded(
        val chapter: MantraChapter,
        val chunks: List<MantraChunk> = emptyList(),
    ): ChapterDetailUIState

    data class Error(
        val message: String
    ): ChapterDetailUIState

    data object Loading: ChapterDetailUIState
}
