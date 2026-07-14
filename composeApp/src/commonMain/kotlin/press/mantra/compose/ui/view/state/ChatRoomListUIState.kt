package press.mantra.compose.ui.view.state

sealed interface ChatRoomListUIState {
    data class Loaded(
        val chatRoomList: List<press.mantra.compose.database.model.intermdiate.LocalChatRoom>
    ): ChatRoomListUIState

    data object Error: ChatRoomListUIState
    data object Loading: ChatRoomListUIState
}

