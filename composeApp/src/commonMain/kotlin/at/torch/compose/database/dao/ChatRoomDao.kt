package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatRoomDao {
    @Transaction
    @Query("SELECT * FROM ChatRoom WHERE id = :id")
    fun findChatRoomById(id: String): at.torch.compose.database.model.intermdiate.LocalChatRoom?

    @Transaction
    @Query("SELECT * FROM ChatRoom WHERE userPublicKey = :userPublicKey")
    fun getChatRoomListByUserPublicKey(userPublicKey: String): List<at.torch.compose.database.model.intermdiate.LocalChatRoom>

    @Transaction
    @Query("SELECT * FROM ChatRoom WHERE userPublicKey = :userPublicKey")
    fun observeChatRoomListByUserPublicKey(userPublicKey: String): Flow<List<at.torch.compose.database.model.intermdiate.LocalChatRoom>>

    @Upsert
    suspend fun upsert(chatRoom: at.torch.compose.database.model.ChatRoom)

}