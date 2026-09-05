package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface ChatRoomDetailUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,
        val artifacts: List<MantraArtifact> = emptyList(),
        val dialects: List<MantraDialect> = emptyList(),
    ): ChatRoomDetailUIState

    data class Error(
        val message: String
    ): ChatRoomDetailUIState
    data object Loading: ChatRoomDetailUIState
}

