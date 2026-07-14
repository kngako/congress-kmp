package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

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

