package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MarmotKeyPackage
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface AddMemberToChatRoomConfirmationUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,
        val profile: Profile,
        val marmotKeyPackage: MarmotKeyPackage
    ): AddMemberToChatRoomConfirmationUIState

    data class Error(
        val message: String
    ): AddMemberToChatRoomConfirmationUIState
    data object Loading: AddMemberToChatRoomConfirmationUIState
}

