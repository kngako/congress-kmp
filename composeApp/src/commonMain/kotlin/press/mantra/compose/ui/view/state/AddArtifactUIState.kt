package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface AddArtifactUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,
        val dialects: List<MantraDialect> = emptyList(),
    ): AddArtifactUIState

    data class Error(
        val message: String
    ): AddArtifactUIState

    data object Loading: AddArtifactUIState
}

