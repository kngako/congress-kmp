package at.torch.compose.ui.view.state

import at.torch.compose.database.model.MarmotKeyPackage
import at.torch.compose.database.model.Profile
import at.torch.compose.database.model.intermdiate.LocalChatRoom
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent

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

