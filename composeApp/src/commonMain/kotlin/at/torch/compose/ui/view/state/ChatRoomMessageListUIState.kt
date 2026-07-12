package at.torch.compose.ui.view.state

sealed interface ChatRoomMessageListUIState {
    data class Loaded(
        val chatMessageList: List<at.torch.compose.database.model.intermdiate.LocalChatMessage>
    ): ChatRoomMessageListUIState

    data object Error: ChatRoomMessageListUIState
    data object Loading: ChatRoomMessageListUIState
}

