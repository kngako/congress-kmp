package ac.aux.compose.database.dao

import ac.aux.compose.database.AuxDatabase
import ac.aux.compose.database.model.BroadcastNostrEventRequest
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Post
import ac.aux.compose.database.model.Profile
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Transaction

@Dao
abstract class NostrDao(
    private val database: AuxDatabase
) {
    @Transaction
    open suspend fun insertNostrEvent(
        nostrEvent: NostrEvent,
        relayURLs: List<String> = emptyList()
    ) {
        database.profileDao().insert(
            Profile(
                publicKey = nostrEvent.pubKey,
                nostrEventId = nostrEvent.id // This is illegal... because the nostrEventId should be the one with all the profile information
            )
        )

        // TODO: Find note that is replyTo
        database.nostrEventDao().insert(nostrEvent)

        nostrEvent.toPost()?.let { post ->
            database.postDao().insert(post)
        }

        nostrEvent.toProfile()?.let { profile ->
            database.profileDao().upsert(profile)
        }

        nostrEvent.toReaction()?.let { reaction ->
            database.reactionDao().upsert(reaction)
        }

        nostrEvent.toRepost()?.let { repost ->
            database.repostDao().upsert(repost)
        }

        nostrEvent.toZap()?.let { zap ->
            database.zapDao().upsert(zap)
        }
        // Submit a broadcastRequest...
        relayURLs.forEach { relayURL ->
            database.broadcastNostrEventRequestDao().upsert(
                BroadcastNostrEventRequest(
                    nostrEventId = nostrEvent.id,
                    relayURL = relayURL
                )
            )
        }
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