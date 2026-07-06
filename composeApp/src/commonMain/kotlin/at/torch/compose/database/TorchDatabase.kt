package at.torch.compose.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.TypeConverters
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import at.torch.compose.database.converters.TorchConverters
import at.torch.compose.database.dao.BroadcastNostrEventReceiptDao
import at.torch.compose.database.dao.BroadcastNostrEventRequestDao
import at.torch.compose.database.dao.ChatMessageBroadcastNostrEventReceiptRelationDao
import at.torch.compose.database.dao.ChatMessageBroadcastNostrEventRequestRelationDao
import at.torch.compose.database.dao.ChatMessageDao
import at.torch.compose.database.dao.ChatMessageNostrEventRelationDao
import at.torch.compose.database.dao.ChatRoomDao
import at.torch.compose.database.dao.ConnectionDao
import at.torch.compose.database.dao.GiftWrapMessageDao
import at.torch.compose.database.dao.GiftWrapPayloadDao
import at.torch.compose.database.dao.GiftWrapSealDao
import at.torch.compose.database.dao.InReplyToRelationDao
import at.torch.compose.database.dao.MarmotCommitResultDao
import at.torch.compose.database.dao.MarmotOutboundDao
import at.torch.compose.database.dao.MarmotGroupEventDao
import at.torch.compose.database.dao.MarmotInnerEventDao
import at.torch.compose.database.dao.MarmotKeyPackageBundleDao
import at.torch.compose.database.dao.MarmotKeyPackageDao
import at.torch.compose.database.dao.MarmotRetainedEpochSecretDao
import at.torch.compose.database.dao.MentionDao
import at.torch.compose.database.dao.NegentropySynchronizeRequestDao
import at.torch.compose.database.dao.NegentropySynchronizeResultDao
import at.torch.compose.database.dao.NostrDao
import at.torch.compose.database.dao.NostrEventDao
import at.torch.compose.database.dao.NostrEventRelayDao
import at.torch.compose.database.dao.NostrNip17Dao
import at.torch.compose.database.dao.ParticipantDao
import at.torch.compose.database.dao.PostDao
import at.torch.compose.database.dao.ProfileDao
import at.torch.compose.database.dao.QuotedRelationDao
import at.torch.compose.database.dao.ReactionDao
import at.torch.compose.database.dao.RecentSearchDao
import at.torch.compose.database.dao.RelayDao
import at.torch.compose.database.dao.RepostedRelationDao
import at.torch.compose.database.dao.SynchronizeNostrEventRequestDao
import at.torch.compose.database.dao.SynchronizeNostrEventResultDao
import at.torch.compose.database.dao.UnsignedNostrEventDao
import at.torch.compose.database.dao.ZapDao
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
import at.torch.compose.database.model.MarmotCommitResult
import at.torch.compose.database.model.MarmotGroupEvent
import at.torch.compose.database.model.MarmotInnerEvent
import at.torch.compose.database.model.MarmotKeyPackage
import at.torch.compose.database.model.MarmotKeyPackageBundle
import at.torch.compose.database.model.MarmotRetainedEpochSecret
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
        MarmotCommitResult::class,
        MarmotGroupEvent::class,
        MarmotInnerEvent::class,
        MarmotKeyPackage::class,
        MarmotKeyPackageBundle::class,
        MarmotRetainedEpochSecret::class,
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
@TypeConverters(TorchConverters::class)
abstract class TorchDatabase: RoomDatabase() {
    abstract fun broadcastNostrEventReceiptDao(): BroadcastNostrEventReceiptDao
    abstract fun broadcastNostrEventRequestDao(): BroadcastNostrEventRequestDao

    abstract fun chatMessageDao(): ChatMessageDao

    abstract fun chatMessageBroadcastNostrEventRequestRelationDao(): ChatMessageBroadcastNostrEventRequestRelationDao

    abstract fun chatMessageBroadcastNostrEventReceiptRelationDao(): ChatMessageBroadcastNostrEventReceiptRelationDao

    abstract fun chatMessageNostrEventRelationDao(): ChatMessageNostrEventRelationDao

    abstract fun chatRoomDao(): ChatRoomDao

    abstract fun connectionDao(): ConnectionDao

    abstract fun giftWrapMessageDao(): GiftWrapMessageDao

    abstract fun giftWrapSealDao(): GiftWrapSealDao

    abstract fun giftWrapPayloadDao(): GiftWrapPayloadDao

    abstract fun inReplyToRelationDao(): InReplyToRelationDao

    abstract fun marmotCommitResultDao(): MarmotCommitResultDao

    abstract fun marmotGroupEventDao(): MarmotGroupEventDao

    abstract fun marmotInnerEventDao(): MarmotInnerEventDao

    abstract fun marmotKeyPackageBundleDao(): MarmotKeyPackageBundleDao

    abstract fun marmotKeyPackageDao(): MarmotKeyPackageDao

    abstract fun marmotOutboundDao(): MarmotOutboundDao

    abstract fun marmotRetainedEpochSecret(): MarmotRetainedEpochSecretDao


    abstract fun mentionDao(): MentionDao

    abstract fun negentropySynchronizeRequestDao(): NegentropySynchronizeRequestDao

    abstract fun negentropySynchronizeResultDao(): NegentropySynchronizeResultDao

    abstract fun nostrDao(): NostrDao

    abstract fun nostrEventDao(): NostrEventDao

    abstract fun nostrEventRelayDao(): NostrEventRelayDao

    abstract fun nostrNip17Dao(): NostrNip17Dao

    abstract fun participantDao(): ParticipantDao

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