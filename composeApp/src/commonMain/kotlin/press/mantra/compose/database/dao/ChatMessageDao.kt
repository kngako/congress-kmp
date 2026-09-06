package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import press.mantra.compose.database.model.ChatMessage
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Transaction
    @Query("SELECT * FROM ChatMessage WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC")
    fun observeChatMessagesByChatRoomId(chatRoomId: String): Flow<List<press.mantra.compose.database.model.intermdiate.LocalChatMessage>>

    @Transaction
    @Query("SELECT * FROM ChatMessage WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC")
    suspend fun getChatMessagesByChatRoomId(chatRoomId: String): List<press.mantra.compose.database.model.intermdiate.LocalChatMessage>

    @Query("SELECT * FROM ChatMessage WHERE giftWrapPayloadId = :giftWrapPayloadId ORDER BY createdAt DESC")
    suspend fun getChatMessagesByGiftWrapPayloadId(giftWrapPayloadId: HexKey): ChatMessage?

    @Query("SELECT * FROM ChatMessage WHERE marmotInnerEventId = :marmotInnerEventId ORDER BY createdAt DESC")
    suspend fun getChatMessagesByMarmotInnerEventId(marmotInnerEventId: HexKey): ChatMessage?

    @Query("SELECT * FROM ChatMessage WHERE marmotGroupEventId = :marmotGroupEventId ORDER BY createdAt DESC")
    suspend fun getChatMessagesByMarmotGroupEventId(marmotGroupEventId: HexKey): ChatMessage?

    @Query("SELECT COUNT(*) FROM ChatMessage WHERE chatRoomId = :chatRoomId AND senderPublicKey = :senderPublicKey")
    suspend fun countChatMessagesBySenderPublicKey(chatRoomId: HexKey, senderPublicKey: HexKey): Int

    /**
     * The room's group events that already have something to show for them.
     *
     * One query so a replay can skip the events it has no business touching without
     * a lookup per event -- `marmotGroupEventId` carries no index, so each of those
     * is a scan of the whole table.
     *
     * [unresolvedTypes] is [press.mantra.compose.database.model.ChatMessage.UNRESOLVED_MARMOT_TYPES]:
     * lines that stand in for a message still to come rather than reporting one.
     */
    @Query(
        "SELECT marmotGroupEventId FROM ChatMessage " +
            "WHERE chatRoomId = :chatRoomId AND marmotGroupEventId IS NOT NULL " +
            "AND messageType NOT IN (:unresolvedTypes)"
    )
    suspend fun getResolvedMarmotGroupEventIds(
        chatRoomId: String,
        unresolvedTypes: Collection<String>
    ): List<HexKey>

    @Upsert
    suspend fun upsert(chatMessage: press.mantra.compose.database.model.ChatMessage): Long
}