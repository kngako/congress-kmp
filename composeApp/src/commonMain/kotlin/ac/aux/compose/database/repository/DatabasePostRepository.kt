package ac.aux.compose.database.repository

import ac.aux.compose.database.AuxDatabase
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.repository.PostRepository

class DatabasePostRepository(
    private val database: AuxDatabase
): PostRepository {
    override suspend fun publishPost(
        nostrEvent: NostrEvent
    ) {
        val nostrDao = database.nostrDao()

        nostrDao.insertNostrEvent(
            nostrEvent
        )
    }
}