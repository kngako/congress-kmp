package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import kotlinx.coroutines.flow.Flow
import press.mantra.compose.database.model.FrostSignerMessage
import press.mantra.compose.database.model.FrostSigningSession

@Dao
interface FrostSigningSessionDao {
    @Query("SELECT * FROM FrostSigningSession WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): FrostSigningSession?

    @Query("SELECT * FROM FrostSigningSession WHERE id = :sessionId")
    fun observeSessionById(sessionId: String): Flow<FrostSigningSession?>

    /**
     * A room's signing sessions, newest first. Unlike a DKG a group signs
     * repeatedly, so there is no single "current" one to observe.
     */
    @Query("SELECT * FROM FrostSigningSession WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC")
    fun observeSessionsForChatRoom(chatRoomId: String): Flow<List<FrostSigningSession>>

    @Query("SELECT * FROM FrostSigningSession WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestSessionForChatRoom(chatRoomId: String): FrostSigningSession?

    @Query("SELECT * FROM FrostSigningSession WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC")
    suspend fun getSessionsForChatRoom(chatRoomId: String): List<FrostSigningSession>

    @Upsert
    suspend fun upsert(frostSigningSession: FrostSigningSession)

    @Upsert
    suspend fun upsert(frostSignerMessage: FrostSignerMessage)

    @Query("SELECT * FROM FrostSignerMessage WHERE sessionId = :sessionId AND kind = :kind ORDER BY createdAt ASC")
    suspend fun getMessagesByKind(sessionId: String, kind: Kind): List<FrostSignerMessage>

    @Query("SELECT * FROM FrostSignerMessage WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeMessages(sessionId: String): Flow<List<FrostSignerMessage>>

    @Query("SELECT COUNT(*) FROM FrostSignerMessage WHERE sessionId = :sessionId AND kind = :kind")
    suspend fun countMessagesByKind(sessionId: String, kind: Kind): Int

    @Query("SELECT * FROM FrostSignerMessage WHERE sessionId = :sessionId AND kind = :kind AND signerPublicKey = :signerPublicKey")
    suspend fun getMessage(sessionId: String, kind: Kind, signerPublicKey: HexKey): FrostSignerMessage?
}
