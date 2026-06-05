package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert

@Dao
interface ChatRoomDao {
    @Transaction
    @Query("SELECT * FROM ChatRoom WHERE id = :id")
    fun findChatRoomById(id: String): LocalChatRoom?

    @Transaction
    @Query("SELECT * FROM ChatRoom WHERE userPublicKey = :userPublicKey")
    fun getChatRoomListByUserPublicKey(userPublicKey: String): List<LocalChatRoom>

    @Upsert
    suspend fun upsert(chatRoom: ChatRoom)

}