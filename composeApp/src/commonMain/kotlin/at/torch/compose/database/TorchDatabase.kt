package at.torch.compose.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.TypeConverters
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import at.torch.compose.database.dao.MarmotInnerEventDao
import at.torch.compose.database.dao.MarmotKeyPackageBundleDao
import at.torch.compose.database.dao.MarmotKeyPackageDao
import at.torch.compose.database.model.BroadcastNostrEventReceipt
import at.torch.compose.database.model.BroadcastNostrEventRequest
import at.torch.compose.database.model.ChatMessage
import at.torch.compose.database.model.ChatMessageBroadcastNostrEventReceiptRelation
import at.torch.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation
import at.torch.compose.database.model.ChatMessageNostrEventRelation
import at.torch.compose.database.model.ChatRoom
import at.torch.compose.database.model.Connection
import at.torch.compose.database.model.GiftWrapMessage
import at.torch.compose.database.model.GiftWrapPayload
import at.torch.compose.database.model.GiftWrapSeal
import at.torch.compose.database.model.InReplyToRelation
import at.torch.compose.database.model.MarmotKeyPackage
import at.torch.compose.database.model.MarmotKeyPackageBundle
import at.torch.compose.database.model.Mention
import at.torch.compose.database.model.NegentropySynchronizeRequest
import at.torch.compose.database.model.NegentropySynchronizeResult
import at.torch.compose.database.model.NostrEvent
import at.torch.compose.database.model.NostrEventRelay
import at.torch.compose.database.model.Participant
import at.torch.compose.database.model.Post
import at.torch.compose.database.model.Profile
import at.torch.compose.database.model.QuotedRelation
import at.torch.compose.database.model.Reaction
import at.torch.compose.database.model.RecentSearch
import at.torch.compose.database.model.Relay
import at.torch.compose.database.model.RepostedRelation
import at.torch.compose.database.model.SynchronizeNostrEventRequest
import at.torch.compose.database.model.SynchronizeNostrEventResult
import at.torch.compose.database.model.UnsignedNostrEvent
import at.torch.compose.database.model.Zap
import kotlin.time.Instant

val GENESIS_AT = Instant.fromEpochMilliseconds(1231006505000L)

@Database(
    entities = [
        BroadcastNostrEventReceipt::class,
        BroadcastNostrEventRequest::class,
        Connection::class,
        ChatMessage::class,
        ChatMessageBroadcastNostrEventRequestRelation::class,
        ChatMessageBroadcastNostrEventReceiptRelation::class,
        ChatMessageNostrEventRelation::class,
        ChatRoom::class,
        GiftWrapMessage::class,
        GiftWrapSeal::class,
        GiftWrapPayload::class,
        InReplyToRelation::class,
        MarmotKeyPackage::class,
        MarmotKeyPackageBundle::class,
        Mention::class,
        NegentropySynchronizeRequest::class,
        NegentropySynchronizeResult::class,
        NostrEvent::class,
        NostrEventRelay::class,
        Participant::class,
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
@TypeConverters(at.torch.compose.database.converters.AuxConverters::class)
abstract class TorchDatabase: RoomDatabase() {
    abstract fun broadcastNostrEventReceiptDao(): at.torch.compose.database.dao.BroadcastNostrEventReceiptDao
    abstract fun broadcastNostrEventRequestDao(): at.torch.compose.database.dao.BroadcastNostrEventRequestDao

    abstract fun chatMessageDao(): at.torch.compose.database.dao.ChatMessageDao

    abstract fun chatMessageBroadcastNostrEventRequestRelationDao(): at.torch.compose.database.dao.ChatMessageBroadcastNostrEventRequestRelationDao

    abstract fun chatMessageBroadcastNostrEventReceiptRelationDao(): at.torch.compose.database.dao.ChatMessageBroadcastNostrEventReceiptRelationDao

    abstract fun chatMessageNostrEventRelationDao(): at.torch.compose.database.dao.ChatMessageNostrEventRelationDao

    abstract fun chatRoomDao(): at.torch.compose.database.dao.ChatRoomDao

    abstract fun connectionDao(): at.torch.compose.database.dao.ConnectionDao

    abstract fun giftWrapMessageDao(): at.torch.compose.database.dao.GiftWrapMessageDao

    abstract fun giftWrapSealDao(): at.torch.compose.database.dao.GiftWrapSealDao

    abstract fun giftWrapPayloadDao(): at.torch.compose.database.dao.GiftWrapPayloadDao

    abstract fun inReplyToRelationDao(): at.torch.compose.database.dao.InReplyToRelationDao

    abstract fun marmotInnerEventDao(): MarmotInnerEventDao

    abstract fun marmotKeyPackageDao(): MarmotKeyPackageDao

    abstract fun marmotKeyPackageBundleDao(): MarmotKeyPackageBundleDao

    abstract fun mentionDao(): at.torch.compose.database.dao.MentionDao

    abstract fun negentropySynchronizeRequestDao(): at.torch.compose.database.dao.NegentropySynchronizeRequestDao

    abstract fun negentropySynchronizeResultDao(): at.torch.compose.database.dao.NegentropySynchronizeResultDao

    abstract fun nostrDao(): at.torch.compose.database.dao.NostrDao

    abstract fun nostrEventDao(): at.torch.compose.database.dao.NostrEventDao

    abstract fun nostrEventRelayDao(): at.torch.compose.database.dao.NostrEventRelayDao

    abstract fun nostrNip17Dao(): at.torch.compose.database.dao.NostrNip17Dao

    abstract fun participantDao(): at.torch.compose.database.dao.ParticipantDao

    abstract fun postDao(): at.torch.compose.database.dao.PostDao

    abstract fun profileDao(): at.torch.compose.database.dao.ProfileDao

    abstract fun quotedRelationDao(): at.torch.compose.database.dao.QuotedRelationDao

    abstract fun reactionDao(): at.torch.compose.database.dao.ReactionDao

    abstract fun recentSearchDao(): at.torch.compose.database.dao.RecentSearchDao

    abstract fun relayDao(): at.torch.compose.database.dao.RelayDao

    abstract fun repostedRelationDao(): at.torch.compose.database.dao.RepostedRelationDao

    abstract fun synchronizeNostrEventRequestDao(): at.torch.compose.database.dao.SynchronizeNostrEventRequestDao

    abstract fun synchronizeNostrEventResultDao(): at.torch.compose.database.dao.SynchronizeNostrEventResultDao

    abstract fun unsignedNostrEventDao(): at.torch.compose.database.dao.UnsignedNostrEventDao
    abstract fun zapDao(): at.torch.compose.database.dao.ZapDao

    suspend fun wipeData() {
        useWriterConnection { transactor ->
            transactor.immediateTransaction {
                // TODO: Actually delete the data...
            }
        }
    }
}