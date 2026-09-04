package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.managers.ChillDkgRitualManager

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
        /**
         * The `n` of the t-of-n. Counted over people, not participant rows — a room
         * can hold the same member twice — and taken from the session once one
         * exists, because that is the count every device agreed to run the ritual on.
         */
        val participantCount: Int
            get() = session?.participantCount
                ?: ChillDkgRitualManager.memberPublicKeys(localChatRoom).size
    }

    data class Error(
        val message: String
    ): DkgRitualUIState

    data object Loading: DkgRitualUIState
}
