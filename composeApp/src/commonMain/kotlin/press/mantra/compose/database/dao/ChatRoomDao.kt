package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import kotlinx.coroutines.flow.Flow

/**
 * The chat-list queries carry the room's newest line with them.
 *
 * A correlated subquery rather than a `GROUP BY chatRoomId` with `MAX(createdAt)`: an
 * [press.mantra.compose.database.model.ChatMessage] timestamp is stored to the second,
 * so a burst of ritual lines written in the same second ties, and the aggregate would
 * pick one of them arbitrarily. Falling back to `id DESC` breaks the tie on the order
 * the rows were actually written, which is the order the transcript shows them in.
 *
 * A room with nothing said in it sorts on when it was created, so a freshly made room
 * appears at the top where the user left it rather than at the bottom under everything.
 */
private const val CHAT_ROOM_WITH_LAST_MESSAGE =
    "SELECT ChatRoom.*, " +
        "lastMessage.senderPublicKey AS lastMessage_senderPublicKey, " +
        "lastMessage.isUserMessage AS lastMessage_isUserMessage, " +
        "lastMessage.content AS lastMessage_content, " +
        "lastMessage.messageType AS lastMessage_messageType, " +
        "lastMessage.directMessageRecipientPublicKey AS lastMessage_directMessageRecipientPublicKey, " +
        "lastMessage.createdAt AS lastMessage_createdAt " +
        "FROM ChatRoom " +
        "LEFT JOIN ChatMessage AS lastMessage ON lastMessage.id = (" +
        "SELECT id FROM ChatMessage " +
        "WHERE chatRoomId = ChatRoom.id AND deletedAt IS NULL " +
        "ORDER BY createdAt DESC, id DESC LIMIT 1" +
        ") "

private const val ORDER_BY_LAST_ACTIVITY =
    "ORDER BY COALESCE(lastMessage.createdAt, ChatRoom.createdAt) DESC, ChatRoom.createdAt DESC"

@Dao
interface ChatRoomDao {
    @Transaction
    @Query(CHAT_ROOM_WITH_LAST_MESSAGE + "WHERE ChatRoom.id = :id AND ChatRoom.deletedAt IS NULL")
    suspend fun findChatRoomById(id: String): LocalChatRoom?

    @Transaction
    @Query(
        CHAT_ROOM_WITH_LAST_MESSAGE +
            "WHERE ChatRoom.userPublicKey = :userPublicKey AND ChatRoom.deletedAt IS NULL " +
            ORDER_BY_LAST_ACTIVITY
    )
    suspend fun getChatRoomListByUserPublicKey(userPublicKey: String): List<LocalChatRoom>

    /**
     * Re-emits when a message lands as well as when a room changes -- the query reads
     * ChatMessage, so Room invalidates it on both, which is what keeps a row's preview
     * and its place in the order current without the list asking.
     */
    @Transaction
    @Query(
        CHAT_ROOM_WITH_LAST_MESSAGE +
            "WHERE ChatRoom.userPublicKey = :userPublicKey AND ChatRoom.deletedAt IS NULL " +
            ORDER_BY_LAST_ACTIVITY
    )
    fun observeChatRoomListByUserPublicKey(userPublicKey: String): Flow<List<LocalChatRoom>>

    @Upsert
    suspend fun upsert(chatRoom: ChatRoom)

    @Delete
    suspend fun delete(chatRoom: ChatRoom)

}
