package ac.cord.auxiliary.compose.ui.view.state

import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom

sealed interface ChatRoomDetailUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom
    ): ChatRoomDetailUIState

    data object Error: ChatRoomDetailUIState
    data object Loading: ChatRoomDetailUIState
}

