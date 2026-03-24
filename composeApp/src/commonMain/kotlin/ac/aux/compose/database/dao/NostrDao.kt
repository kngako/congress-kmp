package ac.aux.compose.database.dao

import ac.aux.compose.database.AuxDatabase
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Post
import androidx.room.Dao
import androidx.room.Transaction

@Dao
abstract class NostrDao(
    private val database: AuxDatabase
) {
    @Transaction
    open suspend fun insertPostAndNostrEvent(post: Post, nostrEvent: NostrEvent) {
        database.nostrEventDao().insert(nostrEvent)
        database.postDao().insert(post) // TODO: Make use of the nostrEventId...
    }
}