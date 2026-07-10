package at.torch.compose.ui.view.state

import at.torch.compose.database.model.intermdiate.LocalChatRoom

sealed interface AddMemberToChatRoomConfirmationUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom
    ): AddMemberToChatRoomConfirmationUIState

    data class Error(
        val message: String
    ): AddMemberToChatRoomConfirmationUIState
    data object Loading: AddMemberToChatRoomConfirmationUIState
}

