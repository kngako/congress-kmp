package at.torch.compose.ui.view.state

sealed interface ChatRoomDetailMessageListUIState {
    data class Loaded(
        val chatMessageList: List<at.torch.compose.database.model.intermdiate.LocalChatMessage>
    ): ChatRoomDetailMessageListUIState

    data object Error: ChatRoomDetailMessageListUIState
    data object Loading: ChatRoomDetailMessageListUIState
}

