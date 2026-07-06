package at.torch.compose.ui.view.state

sealed interface ChatRoomDetailUIState {
    data class Loaded(
        val localChatRoom: at.torch.compose.database.model.intermdiate.LocalChatRoom
    ): ChatRoomDetailUIState

    data class Error(
        val message: String
    ): ChatRoomDetailUIState
    data object Loading: ChatRoomDetailUIState
    data object InitiatingNewChat: ChatRoomDetailUIState
}

