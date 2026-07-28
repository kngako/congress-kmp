package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface AddTranslationArtifactVersionUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,
        val artifact: MantraArtifact,
        val dialects: List<MantraDialect> = emptyList(),
    ): AddTranslationArtifactVersionUIState

    data class Error(
        val message: String
    ): AddTranslationArtifactVersionUIState

    data object Loading: AddTranslationArtifactVersionUIState
}
