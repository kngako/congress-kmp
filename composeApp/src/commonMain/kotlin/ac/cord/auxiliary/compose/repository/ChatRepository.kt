package ac.cord.auxiliary.compose.repository

import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatMessage
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface ChatRepository {
    suspend fun getChatRoomListByPublicKey(publicKey: String): List<LocalChatRoom>

    suspend fun getChatRoomByIdentifier(id: String): LocalChatRoom?

    suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<LocalChatMessage>

    suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<LocalChatMessage>>

    suspend fun getOrCreateChatRoom(publicKey: String, defaultSubject: String? = null): LocalChatRoom?

    fun updateChatRoomSubject(publicKey: String, subject: String): ChatRoom?

    companion object {
        val NO_OP_CHAT_REPOSITORY = object: ChatRepository {
            override suspend fun getChatRoomListByPublicKey(publicKey: String): List<LocalChatRoom> {
                return emptyList()
            }

            override suspend fun getChatRoomByIdentifier(id: String): LocalChatRoom? {
                return null
            }

            override suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<LocalChatMessage> {
                return emptyList()
            }

            override suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<LocalChatMessage>> {
                return flow {
                }
            }

            override suspend fun getOrCreateChatRoom(publicKey: String, defaultSubject: String?): LocalChatRoom? {
                return null
            }

            override fun updateChatRoomSubject(publicKey: String, subject: String): ChatRoom? {
                return null
            }
        }
    }
}