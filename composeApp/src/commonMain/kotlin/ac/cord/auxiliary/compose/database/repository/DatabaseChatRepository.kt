package ac.cord.auxiliary.compose.database.repository

import ac.cord.auxiliary.compose.database.AuxDatabase
import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import ac.cord.auxiliary.compose.repository.ChatRepository
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope

class DatabaseChatRepository(
    private val database: AuxDatabase,
    private val scope: CoroutineScope
): ChatRepository {
    val logger = Logger.withTag(TAG)

    override suspend fun getChatRoomByIdentifier(id: String): LocalChatRoom? {
        return database.chatRoomDao().findChatRoomById(id)
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