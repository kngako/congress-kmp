package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.ChatMessage
import ac.cord.auxiliary.compose.database.model.Relay
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatMessage
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM ChatMessage WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC")
    fun observeChatMessagesByChatRoomId(chatRoomId: String): Flow<List<LocalChatMessage>>

    @Query("SELECT * FROM ChatMessage WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC")
    fun getChatMessagesByChatRoomId(chatRoomId: String): List<LocalChatMessage>

    @Upsert
    suspend fun upsert(chatMessage: ChatMessage)
}