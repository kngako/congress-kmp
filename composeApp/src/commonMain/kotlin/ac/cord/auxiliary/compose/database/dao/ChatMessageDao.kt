package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.ChatMessage
import ac.cord.auxiliary.compose.database.model.Relay
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatMessage
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Transaction
    @Query("SELECT * FROM ChatMessage WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC")
    fun observeChatMessagesByChatRoomId(chatRoomId: String): Flow<List<LocalChatMessage>>

    @Transaction
    @Query("SELECT * FROM ChatMessage WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC")
    fun getChatMessagesByChatRoomId(chatRoomId: String): List<LocalChatMessage>

    @Query("SELECT * FROM ChatMessage WHERE giftWrapPayloadId = :giftWrapPayloadId ORDER BY createdAt DESC")
    fun getChatMessagesByGiftWrapPayloadId(giftWrapPayloadId: Long): ChatMessage?

    @Upsert
    suspend fun upsert(chatMessage: ChatMessage): Long
}