package at.torch.compose.ui.view.state

import at.torch.compose.database.model.Profile
import at.torch.compose.database.model.intermdiate.LocalChatRoom

sealed interface SearchMemberToAddToChatRoomUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,
        val contacts: List<Profile>
    ): SearchMemberToAddToChatRoomUIState

    data class Error(
        val message: String
    ): SearchMemberToAddToChatRoomUIState
    data object Loading: SearchMemberToAddToChatRoomUIState
}

