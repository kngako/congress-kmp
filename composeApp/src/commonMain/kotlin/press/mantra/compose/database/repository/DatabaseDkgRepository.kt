package press.mantra.compose.database.repository

import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.managers.ChillDkgRitualManager
import press.mantra.compose.managers.GroupKeyStateManager
import press.mantra.compose.repository.DkgRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class DatabaseDkgRepository(
    private val database: MantraDatabase,
    private val scope: CoroutineScope
): DkgRepository {
    private val logger = Logger.withTag(TAG)

    override fun observeLatestSessionForChatRoom(chatRoomId: String): Flow<DkgSession?> =
        database.dkgSessionDao().observeLatestSessionForChatRoom(chatRoomId)

    override fun observeMessages(sessionId: String): Flow<List<DkgParticipantMessage>> =
        database.dkgSessionDao().observeMessages(sessionId)

    override suspend fun getLatestSessionForChatRoom(chatRoomId: String): DkgSession? =
        database.dkgSessionDao().getLatestSessionForChatRoom(chatRoomId)

    override suspend fun proposeRitual(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        nostrPrivateKey: ByteArray,
        threshold: Int
    ): DkgSession? = try {
        ChillDkgRitualManager.proposeRitual(
            database = database,
            localChatRoom = localChatRoom,
            userPublicKey = userPublicKey,
            nostrPrivateKey = nostrPrivateKey,
            threshold = threshold
        )
    } catch (e: Throwable) {
        logger.e("Error proposing DKG ritual for ${localChatRoom.chatRoom.id}", e)
        null
    }

    override suspend fun pendingApproval(session: DkgSession): DkgApprovalStep? =
        ChillDkgRitualManager.pendingApproval(database, session)

    override suspend fun proposeGroupKeyState(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        session: DkgSession
    ): FrostSigningSession? = try {
        GroupKeyStateManager.propose(
            database = database,
            localChatRoom = localChatRoom,
            userPublicKey = userPublicKey,
            key = session
        )
    } catch (e: Throwable) {
        // The room not deriving from the key it is about to propose is a bug
        // rather than a condition, and a ceremony with no key yet is a caller
        // that got ahead of itself. Neither is worth failing the room over: the
        // group still has a working chat, and signing simply falls back to the
        // rederivation scan it used before there was a state.
        logger.e("Error proposing the key state for ${localChatRoom.chatRoom.id}", e)
        null
    }

    override suspend fun approve(
        localChatRoom: LocalChatRoom,
        sessionId: String,
        step: DkgApprovalStep,
        nostrPrivateKey: ByteArray
    ) {
        try {
            ChillDkgRitualManager.approve(
                database = database,
                localChatRoom = localChatRoom,
                sessionId = sessionId,
                step = step,
                nostrPrivateKey = nostrPrivateKey
            )
        } catch (e: Throwable) {
            // The ritual fails itself and tells the group; swallowing here keeps a
            // protocol fault from taking the screen down with it.
            logger.e("Error approving $step for DKG ritual $sessionId", e)
        }
    }

    companion object {
        private const val TAG = "DatabaseDkgRepository"
    }
}
