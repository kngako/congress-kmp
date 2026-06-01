package ac.cord.auxiliary.compose.ui.view.state

import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatMessage

sealed interface ChatRoomDetailMessageListUIState {
    data class Loaded(
        val chatMessageList: List<LocalChatMessage>
    ): ChatRoomDetailMessageListUIState

    data object Error: ChatRoomDetailMessageListUIState
    data object Loading: ChatRoomDetailMessageListUIState
}

