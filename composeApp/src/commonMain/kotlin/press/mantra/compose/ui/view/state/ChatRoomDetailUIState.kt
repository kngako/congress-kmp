package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface ChatRoomDetailUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom
    ): ChatRoomDetailUIState

    data class Error(
        val message: String
    ): ChatRoomDetailUIState
    data object Loading: ChatRoomDetailUIState
}

