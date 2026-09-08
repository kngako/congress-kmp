package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface ChatRoomDetailUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,
        val artifacts: List<MantraArtifact> = emptyList(),
        val dialects: List<MantraDialect> = emptyList(),
        /**
         * What the group has said this room signs with, or null while it has
         * said nothing.
         *
         * Sits above everything else on the screen because it is the fact the
         * rest of the room's signed work stands on: which key the room signs as
         * decides whether anything it has signed will be believed.
         */
        val groupKeyState: GroupKeyState? = null,
        /**
         * The event the group signed to say it, as the group signed it.
         *
         * Held beside [groupKeyState] rather than derived from it, because a
         * state is this device's reading and this is the statement -- with the
         * signature on it, which is the part worth copying somewhere it can be
         * checked. Null on a device that holds the reading and not the event.
         */
        val signedGroupKeyStateEvent: GroupSignedEvent? = null,
    ): ChatRoomDetailUIState

    data class Error(
        val message: String
    ): ChatRoomDetailUIState
    data object Loading: ChatRoomDetailUIState
}

