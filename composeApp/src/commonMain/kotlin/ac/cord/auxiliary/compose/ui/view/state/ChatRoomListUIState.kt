package ac.cord.auxiliary.compose.ui.view.state

import ac.cord.auxiliary.compose.database.model.ChatRoom

sealed interface ChatRoomListUIState {
    data class Loaded(
        val chatRoomList: List<ChatRoom>
    ): ChatRoomListUIState

    data object Error: ChatRoomListUIState
    data object Loading: ChatRoomListUIState
}

