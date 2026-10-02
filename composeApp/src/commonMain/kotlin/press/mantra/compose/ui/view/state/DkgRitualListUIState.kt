package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgApprovalStep

sealed interface DkgRitualListUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,

        /** Every ceremony the room holds, newest first. */
        val ceremonies: List<Ceremony> = emptyList(),
    ): DkgRitualListUIState {
        /**
         * The ones that cannot move until this member acts.
         *
         * Split out rather than sorted to the top, the way the proposal list
         * splits its own -- the rest of the list is what the room has done,
         * these are what it is waiting on the reader for. A room can have two
         * at once the moment a subgroup's admins are the whole group, and
         * burying the second under the first one's history is the whole reason
         * this screen exists.
         */
        val waitingForYou: List<Ceremony> = ceremonies.filter { it.awaitsYou }

        val rest: List<Ceremony> = ceremonies.filterNot { it.awaitsYou }
    }

    /**
     * One ceremony, as a row.
     *
     * Both reasons a ceremony can want something are carried separately rather
     * than folded into one flag, because they are owed by different people and
     * read differently: an approval is this member's gate and nobody else can
     * pass it, while finishing the setup is work the coordinator alone can come
     * back to. The row says which.
     */
    data class Ceremony(
        val session: DkgSession,

        /** Whoever opened it, as a name to read. */
        val coordinatorName: String,

        /** What the ceremony is waiting on this member for, if anything. */
        val pendingApproval: DkgApprovalStep? = null,

        /**
         * The room this finished ceremony's key derives, when it does not exist
         * yet and this member is the one who would make it.
         *
         * Reaching COMPLETE finishes nothing on its own: the group has a key and
         * somebody still has to get its state signed and create the room. The
         * same reading the transcript's standing notice makes.
         */
        val roomAwaitingCreation: String? = null,
    ) {
        /** Whether this ceremony is making a subgroup rather than the room's own key. */
        val isSubgroup: Boolean get() = session.parentChatRoomId != null

        val awaitsYou: Boolean get() = pendingApproval != null || roomAwaitingCreation != null
    }

    data class Error(
        val message: String
    ): DkgRitualListUIState

    data object Loading: DkgRitualListUIState
}
