package ac.aux.compose.database

import ac.aux.compose.database.converters.AuxConverters
import ac.aux.compose.database.dao.BroadcastNostrEventReceiptDao
import ac.aux.compose.database.dao.BroadcastNostrEventRequestDao
import ac.aux.compose.database.dao.NostrDao
import ac.aux.compose.database.dao.NostrEventDao
import ac.aux.compose.database.dao.PostDao
import ac.aux.compose.database.dao.ProfileDao
import ac.aux.compose.database.dao.ReactionDao
import ac.aux.compose.database.dao.RecentSearchDao
import ac.aux.compose.database.dao.RepostDao
import ac.aux.compose.database.dao.SynchronizeNostrEventRequestDao
import ac.aux.compose.database.dao.SynchronizeNostrEventResultDao
import ac.aux.compose.database.dao.UnsignedNostrEventDao
import ac.aux.compose.database.dao.ZapDao
import ac.aux.compose.database.model.BroadcastNostrEventReceipt
import ac.aux.compose.database.model.BroadcastNostrEventRequest
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Post
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.Reaction
import ac.aux.compose.database.model.RecentSearch
import ac.aux.compose.database.model.Relay
import ac.aux.compose.database.model.Repost
import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.SynchronizeNostrEventResult
import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.database.model.Zap
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlin.time.Instant

val GENESIS_AT = Instant.fromEpochMilliseconds(1231006505000L)

@Database(
    entities = [
        BroadcastNostrEventReceipt::class,
        BroadcastNostrEventRequest::class,
        NostrEvent::class,
        Post::class,
        Profile::class,
        Reaction::class,
        RecentSearch::class,
        Relay::class,
        Repost::class,
        SynchronizeNostrEventRequest::class,
        SynchronizeNostrEventResult::class,
        UnsignedNostrEvent::class,
        Zap::class
    ],
    version = 1
)
@TypeConverters(AuxConverters::class)
abstract class AuxDatabase: RoomDatabase() {
    abstract fun broadcastNostrEventReceiptDao(): BroadcastNostrEventReceiptDao
    abstract fun broadcastNostrEventRequestDao(): BroadcastNostrEventRequestDao
    abstract fun nostrDao(): NostrDao

    abstract fun nostrEventDao(): NostrEventDao
    abstract fun postDao(): PostDao
    abstract fun profileDao(): ProfileDao
    abstract fun reactionDao(): ReactionDao

    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun repostDao(): RepostDao

    abstract fun synchronizeNostrEventRequestDao(): SynchronizeNostrEventRequestDao

    abstract fun synchronizeNostrEventResultDao(): SynchronizeNostrEventResultDao

    abstract fun unsignedNostrEventDao(): UnsignedNostrEventDao
    abstract fun zapDao(): ZapDao

    suspend fun wipeData() {
        useWriterConnection { transactor ->
            transactor.immediateTransaction {
                // TODO: Actually delete the data...
            }
        }
    }
}