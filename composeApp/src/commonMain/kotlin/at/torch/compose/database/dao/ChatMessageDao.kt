package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import at.torch.compose.database.model.ChatMessage
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Transaction
    @Query("SELECT * FROM ChatMessage WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC")
    fun observeChatMessagesByChatRoomId(chatRoomId: String): Flow<List<at.torch.compose.database.model.intermdiate.LocalChatMessage>>

    @Transaction
    @Query("SELECT * FROM ChatMessage WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC")
    fun getChatMessagesByChatRoomId(chatRoomId: String): List<at.torch.compose.database.model.intermdiate.LocalChatMessage>

    @Query("SELECT * FROM ChatMessage WHERE giftWrapPayloadId = :giftWrapPayloadId ORDER BY createdAt DESC")
    fun getChatMessagesByGiftWrapPayloadId(giftWrapPayloadId: HexKey): ChatMessage?

    @Query("SELECT * FROM ChatMessage WHERE marmotInnerEventId = :marmotInnerEventId ORDER BY createdAt DESC")
    fun getChatMessagesByMarmotInnerEventId(marmotInnerEventId: HexKey): ChatMessage?

    @Query("SELECT * FROM ChatMessage WHERE marmotGroupEventId = :marmotGroupEventId ORDER BY createdAt DESC")
    fun getChatMessagesByMarmotGroupEventId(marmotGroupEventId: HexKey): ChatMessage?

    @Query("SELECT COUNT(*) FROM ChatMessage WHERE chatRoomId = :chatRoomId AND senderPublicKey = :senderPublicKey")
    fun countChatMessagesBySenderPublicKey(chatRoomId: HexKey, senderPublicKey: HexKey): Int

    @Upsert
    suspend fun upsert(chatMessage: at.torch.compose.database.model.ChatMessage): Long
}