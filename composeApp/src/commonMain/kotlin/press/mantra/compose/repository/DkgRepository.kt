package press.mantra.compose.repository

import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
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
     * Asks the room to sign a statement of which ceremony's key it signs with,
     * as its first message.
     *
     * Called once by whoever creates the room. What comes back is the signing
     * session, not the state: the state exists when a quorum has signed, which
     * is the point of proposing it rather than announcing it. Null if the ritual
     * has produced no key yet, or if the room does not derive from the one it
     * produced -- both of which mean there is nothing true to propose.
     */
    suspend fun proposeGroupKeyState(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        session: DkgSession
    ): FrostSigningSession?

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
        }
    }
}
