package ac.cord.auxiliary.compose.repository

import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom

interface ChatRepository {
    suspend fun getChatRoomByIdentifier(id: String): LocalChatRoom?

    suspend fun getOrCreateChatRoom(publicKey: String, defaultSubject: String? = null): LocalChatRoom?

    fun updateChatRoomSubject(publicKey: String, subject: String): ChatRoom?

    companion object {
        val NO_OP_CHAT_REPOSITORY =  object: ChatRepository {
            override suspend fun getChatRoomByIdentifier(id: String): LocalChatRoom? {
                TODO("Not yet implemented")
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