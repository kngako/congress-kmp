package ac.cord.auxiliary.compose.ui.view.state

import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom

sealed interface ChatRoomDetailMessageListUIState {
    data class Loaded(
        val chatRoomList: List<LocalChatRoom>
    ): ChatRoomDetailMessageListUIState

    data object Error: ChatRoomDetailMessageListUIState
    data object Loading: ChatRoomDetailMessageListUIState
}

