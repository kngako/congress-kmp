package ac.cord.auxiliary.compose.database

import ac.cord.auxiliary.compose.database.converters.AuxConverters
import ac.cord.auxiliary.compose.database.dao.BroadcastNostrEventReceiptDao
import ac.cord.auxiliary.compose.database.dao.BroadcastNostrEventRequestDao
import ac.cord.auxiliary.compose.database.dao.ConnectionDao
import ac.cord.auxiliary.compose.database.dao.InReplyToRelationDao
import ac.cord.auxiliary.compose.database.dao.MentionDao
import ac.cord.auxiliary.compose.database.dao.NostrDao
import ac.cord.auxiliary.compose.database.dao.NostrEventDao
import ac.cord.auxiliary.compose.database.dao.PostDao
import ac.cord.auxiliary.compose.database.dao.ProfileDao
import ac.cord.auxiliary.compose.database.dao.QuotedRelationDao
import ac.cord.auxiliary.compose.database.dao.ReactionDao
import ac.cord.auxiliary.compose.database.dao.RecentSearchDao
import ac.cord.auxiliary.compose.database.dao.RelayDao
import ac.cord.auxiliary.compose.database.dao.RepostedRelationDao
import ac.cord.auxiliary.compose.database.dao.SynchronizeNostrEventRequestDao
import ac.cord.auxiliary.compose.database.dao.SynchronizeNostrEventResultDao
import ac.cord.auxiliary.compose.database.dao.UnsignedNostrEventDao
import ac.cord.auxiliary.compose.database.dao.ZapDao
import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventReceipt
import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.Connection
import ac.cord.auxiliary.compose.database.model.InReplyToRelation
import ac.cord.auxiliary.compose.database.model.Mention
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Post
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.QuotedRelation
import ac.cord.auxiliary.compose.database.model.Reaction
import ac.cord.auxiliary.compose.database.model.RecentSearch
import ac.cord.auxiliary.compose.database.model.Relay
import ac.cord.auxiliary.compose.database.model.RepostedRelation
import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest
import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventResult
import ac.cord.auxiliary.compose.database.model.UnsignedNostrEvent
import ac.cord.auxiliary.compose.database.model.Zap
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
        Connection::class,
        InReplyToRelation::class,
        Mention::class,
        NostrEvent::class,
        Post::class,
        Profile::class,
        QuotedRelation::class,
        Reaction::class,
        RecentSearch::class,
        Relay::class,
        RepostedRelation::class,
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
    abstract fun connectionDao(): ConnectionDao

    abstract fun inReplyToRelationDao(): InReplyToRelationDao

    abstract fun mentionDao(): MentionDao

    abstract fun nostrDao(): NostrDao

    abstract fun nostrEventDao(): NostrEventDao
    abstract fun postDao(): PostDao
    abstract fun profileDao(): ProfileDao

    abstract fun quotedRelationDao(): QuotedRelationDao

    abstract fun reactionDao(): ReactionDao

    abstract fun recentSearchDao(): RecentSearchDao

    abstract fun relayDao(): RelayDao

    abstract fun repostedRelationDao(): RepostedRelationDao

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