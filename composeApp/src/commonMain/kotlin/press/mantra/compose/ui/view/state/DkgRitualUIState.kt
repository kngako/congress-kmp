package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface DkgRitualUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,
        /** Null until somebody opens a ritual. */
        val session: DkgSession? = null,
        /** How many members have published each round's message, for the progress ladder. */
        val hostKeyCount: Int = 0,
        val round1Count: Int = 0,
        val round2Count: Int = 0,
    ): DkgRitualUIState {
        val participantCount: Int get() = session?.participantCount ?: localChatRoom.localParticipants.size
    }

    data class Error(
        val message: String
    ): DkgRitualUIState

    data object Loading: DkgRitualUIState
}
