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

    @Upsert
    suspend fun upsert(chatMessage: press.mantra.compose.database.model.ChatMessage): Long
}