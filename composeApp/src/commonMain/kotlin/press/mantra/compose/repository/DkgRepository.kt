package press.mantra.compose.repository

import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Reads and starts ChillDKG rituals. Advancing one is the inbound path's job. */
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
        }
    }
}
