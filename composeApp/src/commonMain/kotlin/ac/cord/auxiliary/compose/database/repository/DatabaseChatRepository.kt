package ac.cord.auxiliary.compose.database.repository

import ac.cord.auxiliary.compose.database.AuxDatabase
import ac.cord.auxiliary.compose.database.model.ChatMessage
import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.GiftWrapPayload
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatMessage
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import ac.cord.auxiliary.compose.repository.ChatRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import fr.acinq.lightning.utils.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class DatabaseChatRepository(
    private val database: AuxDatabase,
    private val scope: CoroutineScope
): ChatRepository {
    val logger = Logger.withTag(TAG)

    override suspend fun getChatRoomListByPublicKey(publicKey: String): List<LocalChatRoom> {
        return database.chatRoomDao().getChatRoomListByUserPublicKey(publicKey)
    }

    override suspend fun getChatRoomByIdentifier(id: String): LocalChatRoom? {
        return database.chatRoomDao().findChatRoomById(id)
    }

    override suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<LocalChatMessage> {
        return database.chatMessageDao().getChatMessagesByChatRoomId(chatRoomId)
    }

    override suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<LocalChatMessage>> {
        return database.chatMessageDao().observeChatMessagesByChatRoomId(chatRoomId)
    }


    override suspend fun getOrCreateChatRoom(publicKey: String, defaultSubject: String?): LocalChatRoom? = try {
        return database.nostrNip17Dao().getOrCreateChatRoom(
            publicKey,
            defaultSubject = defaultSubject
        )
    } catch (e: Throwable) {
        logger.e("Error getting or creating chat", e)
        return null
    }

    override suspend fun sendChatMessage(
        text: String,
        chatRoom: ChatRoom
    ) {
        logger.i("sendChatMessage($text): $chatRoom")
        database.chatMessageDao().upsert(
            ChatMessage(
                content = text,
                chatRoomId = chatRoom.id,
                senderPublicKey = chatRoom.userPublicKey,
                isUserMessage = true
            )
        )
    }

    override fun updateChatRoomSubject(
        publicKey: String,
        subject: String
    ): ChatRoom? {
        return null
    }

    companion object {
        const val TAG = "DatabaseChatRepository"
    }
}