package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.GroupKeyState
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
        /**
         * Members whose key package the coordinator could not find, and so who stop
         * the admin room being created at all.
         *
         * Names rather than keys, because the answer to this is to go and ask that
         * person to open the app. Empty when nothing is blocked, which is also the
         * state a retry starts from.
         */
        val adminGroupBlockedOn: List<String> = emptyList(),
        /**
         * The session in which the group is agreeing what its admin room will
         * sign with, once somebody has opened one.
         *
         * A ceremony's room holds exactly one of these and nothing else, because
         * a NIP-17 room signs nothing else -- see `GroupKeyStateManager.propose`.
         * Null before it is proposed, and it stays on the screen afterwards so
         * the group can see how far the signing has got.
         */
        val keyStateSession: FrostSigningSession? = null,
        /**
         * Whether the group has finished signing that statement.
         *
         * Read from the signed event rather than from the [GroupKeyState] table,
         * because the room the state is about does not exist yet -- being able
         * to say this before there is a room is the whole reason the state is
         * signed first. It is what gates creating the room.
         */
        val isKeyStateSigned: Boolean = false,
        /**
         * Whether this room already holds a ceremony that has not failed --
         * whichever one [session] happens to be showing.
         *
         * Two different questions, and conflating them opened a second ceremony
         * in a room that already had one. [session] is the ceremony being looked
         * at; this is whether the room has any. `proposeRitual`'s guard is scoped
         * by purpose, so a room holding a subgroup's ceremony would happily take
         * a second one for no subgroup -- and then "the room's newest ceremony"
         * is the new empty one and the subgroup's is buried under it.
         */
        val roomHasCeremony: Boolean = false,
        /**
         * The group this ceremony is making a subgroup of, or null for an
         * ordinary one.
         *
         * A claim off the route and the proposal, not evidence. It decides which
         * ladder the screen draws and nothing else -- what makes a lineage real
         * is [isCertified] below, which is a signature by that group's quorum.
         */
        val parentChatRoomId: HexKey? = null,
        /** Whether the parent has certified the room this ceremony's key derives. */
        val isCertified: Boolean = false,
        /**
         * The session asking the parent to certify, while it is running.
         *
         * Runs in the *parent's* room rather than this one, so it is watched
         * separately from [keyStateSession] and cannot be read off this room's
         * sessions at all.
         */
        val certificateSession: FrostSigningSession? = null,
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
