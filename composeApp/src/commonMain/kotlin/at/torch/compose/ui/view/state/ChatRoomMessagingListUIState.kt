package at.torch.compose.ui.view.state

import at.torch.compose.database.model.intermdiate.LocalChatRoom

sealed interface ChatRoomMessagingListUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom
    ): ChatRoomMessagingListUIState

    data class Error(
        val message: String
    ): ChatRoomMessagingListUIState
    data object Loading: ChatRoomMessagingListUIState
    data object InitiatingNewChat: ChatRoomMessagingListUIState
}

