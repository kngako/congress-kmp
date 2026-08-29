package press.mantra.compose.database.repository

import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.managers.ChillDkgRitualManager
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

    companion object {
        private const val TAG = "DatabaseDkgRepository"
    }
}
