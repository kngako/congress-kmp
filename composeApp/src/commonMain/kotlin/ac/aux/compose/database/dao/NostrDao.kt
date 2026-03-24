package ac.aux.compose.database.dao

import ac.aux.compose.database.AuxDatabase
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Post
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Transaction

@Dao
abstract class NostrDao(
    private val database: AuxDatabase
) {
    @Transaction
    open suspend fun insertNostrEvent(nostrEvent: NostrEvent) {
        // TODO: Find or create profile with nostrEvent.pubkey

        database.nostrEventDao().insert(nostrEvent)

        // TODO: Get post from nostrEvent
//        database.postDao().insert(nostrEvent.post) // TODO: Make use of the nostrEventId...
    }

    data class NostrEventAndPost(
        @Embedded val nostrEvent: NostrEvent,
        @Relation(
            parentColumn = "id",
            entityColumn = "nostrEventId"
        )
        val post: Post
    )
}