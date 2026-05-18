package ac.cord.auxiliary.compose.database

import ac.cord.auxiliary.compose.database.converters.AuxConverters
import ac.cord.auxiliary.compose.database.dao.BroadcastNostrEventReceiptDao
import ac.cord.auxiliary.compose.database.dao.BroadcastNostrEventRequestDao
import ac.cord.auxiliary.compose.database.dao.ChatMessageDao
import ac.cord.auxiliary.compose.database.dao.ChatRoomDao
import ac.cord.auxiliary.compose.database.dao.ConnectionDao
import ac.cord.auxiliary.compose.database.dao.GiftWrapMessageDao
import ac.cord.auxiliary.compose.database.dao.GiftWrapSealDao
import ac.cord.auxiliary.compose.database.dao.GiftWrapUnsignedDao
import ac.cord.auxiliary.compose.database.dao.InReplyToRelationDao
import ac.cord.auxiliary.compose.database.dao.MentionDao
import ac.cord.auxiliary.compose.database.dao.NegentropySynchronizeRequestDao
import ac.cord.auxiliary.compose.database.dao.NegentropySynchronizeResultDao
import ac.cord.auxiliary.compose.database.dao.NostrDao
import ac.cord.auxiliary.compose.database.dao.NostrEventDao
import ac.cord.auxiliary.compose.database.dao.NostrEventRelayDao
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
import ac.cord.auxiliary.compose.database.model.ChatMessage
import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.Connection
import ac.cord.auxiliary.compose.database.model.GiftWrapMessage
import ac.cord.auxiliary.compose.database.model.GiftWrapSeal
import ac.cord.auxiliary.compose.database.model.GiftWrapUnsigned
import ac.cord.auxiliary.compose.database.model.InReplyToRelation
import ac.cord.auxiliary.compose.database.model.Mention
import ac.cord.auxiliary.compose.database.model.NegentropySynchronizeRequest
import ac.cord.auxiliary.compose.database.model.NegentropySynchronizeResult
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.NostrEventRelay
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
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.TypeConverters
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import kotlin.time.Instant

val GENESIS_AT = Instant.fromEpochMilliseconds(1231006505000L)

@Database(
    entities = [
        BroadcastNostrEventReceipt::class,
        BroadcastNostrEventRequest::class,
        Connection::class,
        ChatMessage::class,
        ChatRoom::class,
        InReplyToRelation::class,
        GiftWrapMessage::class,
        GiftWrapSeal::class,
        GiftWrapUnsigned::class,
        Mention::class,
        NegentropySynchronizeRequest::class,
        NegentropySynchronizeResult::class,
        NostrEvent::class,
        NostrEventRelay::class,
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
    version = 2,
    autoMigrations = [
        AutoMigration(
            from = 1,
            to = 2
        )
    ]
)
@TypeConverters(AuxConverters::class)
abstract class AuxDatabase: RoomDatabase() {
    abstract fun broadcastNostrEventReceiptDao(): BroadcastNostrEventReceiptDao
    abstract fun broadcastNostrEventRequestDao(): BroadcastNostrEventRequestDao

    abstract fun chatMessageDao(): ChatMessageDao

    abstract fun chatRoomDao(): ChatRoomDao

    abstract fun connectionDao(): ConnectionDao

    abstract fun giftWrapMessageDao(): GiftWrapMessageDao

    abstract fun giftWrapSealDao(): GiftWrapSealDao

    abstract fun giftWrapUnsignedDao(): GiftWrapUnsignedDao

    abstract fun inReplyToRelationDao(): InReplyToRelationDao

    abstract fun mentionDao(): MentionDao

    abstract fun negentropySynchronizeRequestDao(): NegentropySynchronizeRequestDao

    abstract fun negentropySynchronizeResultDao(): NegentropySynchronizeResultDao

    abstract fun nostrDao(): NostrDao

    abstract fun nostrEventDao(): NostrEventDao

    abstract fun nostrEventRelayDao(): NostrEventRelayDao

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