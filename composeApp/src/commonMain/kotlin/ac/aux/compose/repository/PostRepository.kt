package ac.aux.compose.repository

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Post

interface PostRepository {
    suspend fun publishPost(post: Post, nostrEvent: NostrEvent)
}