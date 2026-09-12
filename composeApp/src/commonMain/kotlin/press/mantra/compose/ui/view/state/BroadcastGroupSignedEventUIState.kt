package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.nostr.GroupRelayList

sealed interface BroadcastGroupSignedEventUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,
        /** The event to send, as the group signed it. What goes on the wire is this, byte for byte. */
        val signedEvent: GroupSignedEvent,
        /**
         * Where the group has said it lives, which is what the relay picker starts
         * from: the General list's write relays when the group has agreed one, and
         * never a relay the group has blocked. See
         * `BroadcastGroupSignedEventViewModel.defaultRelaysFor`.
         */
        val relayLists: List<GroupRelayList> = emptyList(),
    ): BroadcastGroupSignedEventUIState

    data class Error(
        val message: String
    ): BroadcastGroupSignedEventUIState

    data object Loading: BroadcastGroupSignedEventUIState
}
