package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import at.torch.compose.database.model.ChatRoom
import at.torch.compose.database.model.intermdiate.LocalChatRoom
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatRoomDao {
    @Transaction
    @Query("SELECT * FROM ChatRoom WHERE id = :id")
    fun findChatRoomById(id: String): LocalChatRoom?

    @Transaction
    @Query("SELECT * FROM ChatRoom WHERE userPublicKey = :userPublicKey")
    fun getChatRoomListByUserPublicKey(userPublicKey: String): List<LocalChatRoom>

    @Transaction
    @Query("SELECT * FROM ChatRoom WHERE userPublicKey = :userPublicKey")
    fun observeChatRoomListByUserPublicKey(userPublicKey: String): Flow<List<LocalChatRoom>>

    @Upsert
    suspend fun upsert(chatRoom: ChatRoom)

    @Delete
    suspend fun delete(chatRoom: ChatRoom)

}