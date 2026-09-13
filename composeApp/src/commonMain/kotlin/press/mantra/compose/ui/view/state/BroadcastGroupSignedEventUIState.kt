package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface BroadcastGroupSignedEventUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,
        /** The event to send, as the group signed it. What goes on the wire is this, byte for byte. */
        val signedEvent: GroupSignedEvent,
    ): BroadcastGroupSignedEventUIState

    data class Error(
        val message: String
    ): BroadcastGroupSignedEventUIState

    data object Loading: BroadcastGroupSignedEventUIState
}
