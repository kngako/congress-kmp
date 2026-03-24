package ac.aux.compose.repository

import ac.aux.compose.database.model.NostrEvent

interface PostRepository {
    suspend fun publishPost(nostrEvent: NostrEvent)
}