package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.Relay
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import fr.acinq.bitcoin.PublicKey
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatRoomDao {

    @Query("SELECT * FROM ChatRoom WHERE userPublicKey = :userPublicKey")
    fun observeChatRoomByUserPublicKey(userPublicKey: String): Flow<List<ChatRoom>>

    @Upsert
    suspend fun upsert(chatRoom: ChatRoom)

}