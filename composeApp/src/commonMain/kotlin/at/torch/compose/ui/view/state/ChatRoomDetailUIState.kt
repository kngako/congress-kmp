package at.torch.compose.ui.view.state

sealed interface ChatRoomDetailUIState {
    data class Loaded(
        val localChatRoom: at.torch.compose.database.model.intermdiate.LocalChatRoom
    ): ChatRoomDetailUIState

    data object Error: ChatRoomDetailUIState
    data object Loading: ChatRoomDetailUIState
}

