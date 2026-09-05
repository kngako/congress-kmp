package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.intermdiate.LocalParticipant
import press.mantra.compose.database.model.types.DkgApprovalStep
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.managers.ChillDkgRitualManager

sealed interface DkgRitualUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,
        /** Null until somebody opens a ritual. */
        val session: DkgSession? = null,
        /**
         * Who has published each round's message, for the progress ladder.
         *
         * Members rather than counts, because the one thing this screen exists to
         * answer is which member the ceremony is waiting on — a DKG cannot finish
         * until every one of them has taken part, and "2 of 3" does not tell anyone
         * whose door to knock on.
         */
        val hostKeyParticipants: Set<HexKey> = emptySet(),
        val round1Participants: Set<HexKey> = emptySet(),
        val round2Participants: Set<HexKey> = emptySet(),
        /**
         * What the ritual is waiting on this device's owner for, if anything.
         *
         * Null covers two very different situations that look the same from here —
         * waiting on somebody else, and nothing left to wait for — because the
         * screen already distinguishes them from [session]'s stage. What it cannot
         * derive is this, which needs the stored messages as well as the row.
         */
        val pendingApproval: DkgApprovalStep? = null,
    ): DkgRitualUIState {
        val hostKeyCount: Int get() = hostKeyParticipants.size
        val round1Count: Int get() = round1Participants.size
        val round2Count: Int get() = round2Participants.size

        /**
         * Everyone the ceremony involves, as far as this device can tell.
         *
         * The room's members, plus anyone who has actually taken part. The union
         * matters because the two can disagree: `n` is fixed from the proposal's
         * p-tags, while the room's rows are local and can drift, and a participant
         * who has published a host key is in the ceremony whatever the room says.
         */
        val ritualMembers: List<LocalParticipant> get() {
            val known = localChatRoom.localParticipants.distinctBy { it.participant.participantPublicKey }
            val knownKeys = known.map { it.participant.participantPublicKey }.toSet()

            val strangers = (hostKeyParticipants + round1Participants + round2Participants)
                .filterNot { it in knownKeys }
                .map { LocalParticipant(Participant(participantPublicKey = it, chatRoomId = localChatRoom.chatRoom.id, relayHint = null), null) }

            return known + strangers
        }

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
