package ac.cord.auxiliary.compose.database.repository

import ac.cord.auxiliary.compose.database.AuxDatabase
import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import ac.cord.auxiliary.compose.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope

class DatabaseChatRepository(
    private val database: AuxDatabase,
    private val scope: CoroutineScope
): ChatRepository {

    override suspend fun getOrCreateChatRoom(publicKey: String, defaultSubject: String?): LocalChatRoom? {
        return database.nostrNip17Dao().getOrCreateChatRoom(
            publicKey,
            defaultSubject = defaultSubject
        )
    }

    override fun updateChatRoomSubject(
        publicKey: String,
        subject: String
    ): ChatRoom? {
        return null
    }
}