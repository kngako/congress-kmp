package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.MantraTranslationChapter

/** A translation chapter with how many of its chunks have been translated. */
data class TranslationChapterProgress(
    val chapter: MantraTranslationChapter,
    val totalChunks: Int,
    val translatedChunks: Int,
)

sealed interface TranslationDetailUIState {
    data class Loaded(
        val translation: MantraTranslationArtifactVersion,
        val chapters: List<TranslationChapterProgress> = emptyList(),
    ): TranslationDetailUIState

    data class Error(
        val message: String
    ): TranslationDetailUIState

    data object Loading: TranslationDetailUIState
}
