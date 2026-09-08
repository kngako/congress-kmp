package press.mantra.compose.repository

import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.intermdiate.LocalFrostSigningSession
import press.mantra.compose.database.model.types.DkgApprovalStep
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Reads, starts and approves ChillDKG rituals.
 *
 * Advancing one is the inbound path's job, with one exception: a ritual waiting on
 * this device's owner does not advance until [approve] is called, because it will
 * not publish on their behalf until they say so.
 */
interface DkgRepository {
    /** The room's current ritual — abandoned attempts are superseded by the newest. */
    fun observeLatestSessionForChatRoom(chatRoomId: String): Flow<DkgSession?>

    fun observeMessages(sessionId: String): Flow<List<DkgParticipantMessage>>

    suspend fun getLatestSessionForChatRoom(chatRoomId: String): DkgSession?

    /** Opens a ritual. Only meaningful for the room's creator. */
    suspend fun proposeRitual(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        nostrPrivateKey: ByteArray,
        threshold: Int
    ): DkgSession?

    /** What the ritual is waiting on this device's owner for, if anything. */
    suspend fun pendingApproval(session: DkgSession): DkgApprovalStep?

    /** Agrees to one step, letting the ritual publish it and run on. */
    suspend fun approve(
        localChatRoom: LocalChatRoom,
        sessionId: String,
        step: DkgApprovalStep,
        nostrPrivateKey: ByteArray
    )

    /**
     * Asks the group to sign a statement of which ceremony's key its #admins
     * room will sign with, before that room is created.
     *
     * Called once by the ceremony's coordinator, in the NIP-17 room the ceremony
     * ran in. What comes back is the signing session, not the state: the state
     * exists when a quorum has signed, which is the point of proposing it rather
     * than announcing it. Null if the ritual has produced no key yet, or if this
     * room has no standing to propose one -- both of which mean there is nothing
     * true to propose.
     */
    suspend fun proposeGroupKeyState(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        session: DkgSession
    ): FrostSigningSession?

    /**
     * The signing sessions open in a room, newest first, with what each signs.
     *
     * Read by the ritual screen to follow the one session a NIP-17 room ever
     * holds: the group agreeing its key state. The items travel with the session
     * because "which session is this" is answered by the kind of the event it is
     * signing, not by the row.
     */
    fun observeSigningSessions(chatRoomId: String): Flow<List<LocalFrostSigningSession>>

    /**
     * The key state the group has signed for the #admins room this room's
     * ceremony will make, whether or not that room exists yet.
     *
     * The gate on creating it. Null means the group has not finished agreeing
     * what it will sign with, and a room created then would be one whose
     * founding fact was never settled.
     *
     * A flow over the signed events rather than a read, because the moment it
     * changes is the moment there is a room to make -- and the state it is
     * about does not exist as a row until somebody makes one.
     */
    fun observeSignedGroupKeyState(chatRoomId: String): Flow<GroupKeyState?>

    /** Files the state the group signed for a room that now exists. */
    suspend fun adoptGroupKeyState(chatRoomId: String): GroupKeyState?

    companion object {
        val NO_OP_DKG_REPOSITORY: DkgRepository = object : DkgRepository {
            override fun observeLatestSessionForChatRoom(chatRoomId: String): Flow<DkgSession?> = flowOf(null)

            override fun observeMessages(sessionId: String): Flow<List<DkgParticipantMessage>> = flowOf(emptyList())

            override suspend fun getLatestSessionForChatRoom(chatRoomId: String): DkgSession? = null

            override suspend fun proposeRitual(
                localChatRoom: LocalChatRoom,
                userPublicKey: HexKey,
                nostrPrivateKey: ByteArray,
                threshold: Int
            ): DkgSession? = null

            override suspend fun pendingApproval(session: DkgSession): DkgApprovalStep? = null

            override suspend fun approve(
                localChatRoom: LocalChatRoom,
                sessionId: String,
                step: DkgApprovalStep,
                nostrPrivateKey: ByteArray
            ) = Unit

            override suspend fun proposeGroupKeyState(
                localChatRoom: LocalChatRoom,
                userPublicKey: HexKey,
                session: DkgSession
            ): FrostSigningSession? = null

            override fun observeSigningSessions(
                chatRoomId: String
            ): Flow<List<LocalFrostSigningSession>> = flowOf(emptyList())

            override fun observeSignedGroupKeyState(chatRoomId: String): Flow<GroupKeyState?> =
                flowOf(null)

            override suspend fun adoptGroupKeyState(chatRoomId: String): GroupKeyState? = null
        }
    }
}
