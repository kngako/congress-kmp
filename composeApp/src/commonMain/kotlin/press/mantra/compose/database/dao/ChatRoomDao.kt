package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.ChatRoomMembership
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

/**
 * Whose rooms a listing is of, and which of them they have tidied away.
 *
 * Both are the membership's now rather than the room's -- a room belongs to the group,
 * and "mine" and "archived" are things one profile says about it. Joined rather than
 * filtered on the embedded row, so the other profile's archiving cannot take a room out
 * of this one's list.
 */
private const val FOR_MEMBER =
    "JOIN ChatRoomMembership AS membership ON membership.chatRoomId = ChatRoom.id " +
        "AND membership.userPublicKey = :userPublicKey " +
        "AND membership.deletedAt IS NULL "

@Dao
interface ChatRoomDao {
    /**
     * A group by its id, whoever is asking.
     *
     * Deliberately not scoped by profile and deliberately not filtered by anybody's
     * `deletedAt`. What a caller wants from this is the group it is about to act in --
     * its name, its description, the row the twenty-two tables hang off -- and that exists
     * whether or not somebody archived their chat-list row for it. The memberships come
     * with it, so a caller needing one profile's MLS state asks
     * `LocalChatRoom.membershipOf`.
     */
    @Transaction
    @Query(CHAT_ROOM_WITH_LAST_MESSAGE + "WHERE ChatRoom.id = :id")
    suspend fun findChatRoomById(id: String): LocalChatRoom?

    @Transaction
    @Query(CHAT_ROOM_WITH_LAST_MESSAGE + FOR_MEMBER + ORDER_BY_LAST_ACTIVITY)
    suspend fun getChatRoomListByUserPublicKey(userPublicKey: String): List<LocalChatRoom>

    /**
     * Re-emits when a message lands as well as when a room changes -- the query reads
     * ChatMessage, so Room invalidates it on both, which is what keeps a row's preview
     * and its place in the order current without the list asking.
     */
    @Transaction
    @Query(CHAT_ROOM_WITH_LAST_MESSAGE + FOR_MEMBER + ORDER_BY_LAST_ACTIVITY)
    fun observeChatRoomListByUserPublicKey(userPublicKey: String): Flow<List<LocalChatRoom>>

    @Query(
        "SELECT * FROM ChatRoomMembership WHERE chatRoomId = :chatRoomId " +
            "AND userPublicKey = :userPublicKey"
    )
    suspend fun findMembership(chatRoomId: String, userPublicKey: String): ChatRoomMembership?

    /** Every profile on this device that is a member of the group. */
    @Query("SELECT * FROM ChatRoomMembership WHERE chatRoomId = :chatRoomId")
    suspend fun membershipsOf(chatRoomId: String): List<ChatRoomMembership>

    @Upsert
    suspend fun upsert(membership: ChatRoomMembership)

    @Delete
    suspend fun delete(membership: ChatRoomMembership)

    // "The subgroups of this room" used to be a column filter here, over a
    // ChatRoom.parentChatRoomId that production never read -- the live answer
    // went through GroupKeyState, and SubgroupManager.subgroupsOf reads the
    // parent's own certificates, which can name a child whose room this device
    // does not have. The column is gone at v21, because a room can have more
    // than one parent and a single column would have held one of them. The
    // rooms a parent's children occupy are reached through
    // GroupKeyStateDao.getByParentChatRoomId and resolved from there.

    @Upsert
    suspend fun upsert(chatRoom: ChatRoom)

    @Delete
    suspend fun delete(chatRoom: ChatRoom)

}
