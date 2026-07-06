package at.torch.compose.ui.view.state

import at.torch.compose.database.model.intermdiate.LocalChatRoom

sealed interface ChatRoomDetailUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom
    ): ChatRoomDetailUIState

    data class Error(
        val message: String
    ): ChatRoomDetailUIState
    data object Loading: ChatRoomDetailUIState
    data object InitiatingNewChat: ChatRoomDetailUIState
}

